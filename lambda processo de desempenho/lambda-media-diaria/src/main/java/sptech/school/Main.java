package sptech.school;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Main implements RequestHandler<S3Event, String> {

    private final AmazonS3 s3Client = AmazonS3ClientBuilder.defaultClient();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final String BUCKET_SAIDA = "sentinela-trusted-bucket";

    @Override
    public String handleRequest(S3Event s3Event, Context context) {
        String sourceBucket = s3Event.getRecords().get(0).getS3().getBucket().getName();
        String sourceKey = s3Event.getRecords().get(0).getS3().getObject().getKey();

        context.getLogger().log("Processando arquivo: " + sourceKey + " do bucket: " + sourceBucket);

        try {
            // Exemplo de nome de arquivo: acme_2025-06-01_modeloX_captura.json
            String[] partes = sourceKey.split("_");
            if (partes.length < 4) {
                throw new IllegalArgumentException("Nome do arquivo (key) inválido. Esperado formato 'empresa_data_modelo_...': " + sourceKey);
            }
            String empresa = partes[0];
            String data = partes[1];
            String modelo = partes[2];

            DadosWrapper wrapper;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    s3Client.getObject(sourceBucket, sourceKey).getObjectContent(), StandardCharsets.UTF_8))) {
                wrapper = objectMapper.readValue(reader, DadosWrapper.class);
            }

            List<MediaDiariaResultado> resultados = processarDados(wrapper.getDados(), empresa, data);

            String keySaida = String.format("media-diaria/%s/%s/%s.json", empresa, modelo, data);

            byte[] jsonBytes = objectMapper.writeValueAsBytes(resultados);

            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(jsonBytes.length);
            metadata.setContentType("application/json");

            s3Client.putObject(BUCKET_SAIDA, keySaida, new ByteArrayInputStream(jsonBytes), metadata);

            context.getLogger().log("Processamento finalizado. Arquivo salvo em: s3://" + BUCKET_SAIDA + "/" + keySaida);
            return "Processamento finalizado para " + sourceKey;

        } catch (Exception e) {
            context.getLogger().log("ERRO ao processar " + sourceKey + ": " + e.getMessage());
            throw new RuntimeException("Erro no processamento: " + e.getMessage(), e);
        }
    }

    private static List<MediaDiariaResultado> processarDados(Map<String, List<Medicao>> dados, String empresa, String data) {
        List<Medicao> listaUptime = dados.get("uptime_hours");
        Map<ChaveAgrupamento, List<Medicao>> agrupados = new HashMap<>();

        for (Map.Entry<String, List<Medicao>> entry : dados.entrySet()) {
            String metrica = entry.getKey();
            if ("uptime_hours".equals(metrica)) {
                continue;
            }

            List<Medicao> medicoes = entry.getValue();
            for (int i = 0; i < medicoes.size(); i++) {
                Medicao medicao = medicoes.get(i);

                if (listaUptime != null && i < listaUptime.size()) {
                    medicao.setUptimeHours(listaUptime.get(i).getValor());
                }

                ChaveAgrupamento chave = new ChaveAgrupamento(
                        medicao.getModelo(),
                        empresa,
                        metrica,
                        data
                );
                agrupados.computeIfAbsent(chave, k -> new ArrayList<>()).add(medicao);
            }
        }

        List<MediaDiariaResultado> resultados = new ArrayList<>();
        for (Map.Entry<ChaveAgrupamento, List<Medicao>> entry : agrupados.entrySet()) {
            ChaveAgrupamento chave = entry.getKey();
            List<Medicao> medicoesAgrupadas = entry.getValue();

            double somaGeral = 0.0, somaPico = 0.0;
            double uptimeTotal = 0.0, uptimePico = 0.0;
            int contador = 0, contadorPico = 0, contadorAcima95 = 0;

            for (Medicao m : medicoesAgrupadas) {
                double valor = m.getValor();
                double uptime = m.getUptimeHours() != null ? m.getUptimeHours() : 0.0;

                somaGeral += valor;
                uptimeTotal += uptime;
                contador++;

                if (valor >= 70.0) {
                    somaPico += valor;
                    uptimePico += uptime;
                    contadorPico++;
                }
                if (valor >= 95.0) {
                    contadorAcima95++;
                }
            }

            double mediaGeral = (contador > 0) ? somaGeral / contador : 0.0;
            double mediaPico = (contadorPico > 0) ? somaPico / contadorPico : 0.0;

            resultados.add(new MediaDiariaResultado(
                    chave.getModelo(), chave.getEmpresa(), chave.getMetrica(), chave.getData(),
                    mediaGeral, mediaPico, uptimeTotal, uptimePico, contadorAcima95
            ));
        }
        return resultados;
    }
}