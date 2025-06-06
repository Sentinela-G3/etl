package sptech.school;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.*;

public class Main implements RequestHandler<S3Event, String> {

    private final AmazonS3 s3Client = AmazonS3ClientBuilder.defaultClient();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final String BUCKET_SAIDA = "trustedbuckets3-spt";

    @Override
    public String handleRequest(S3Event s3Event, Context context) {
        String sourceBucket = s3Event.getRecords().get(0).getS3().getBucket().getName();
        String sourceKey = s3Event.getRecords().get(0).getS3().getObject().getKey();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                s3Client.getObject(sourceBucket, sourceKey).getObjectContent()))) {

            String[] partes = sourceKey.split("_");
            if (partes.length < 3) {
                throw new IllegalArgumentException("Formato de chave inválido: " + sourceKey);
            }

            String empresa = partes[0];
            String data = partes[1];

            DadosWrapper wrapper = objectMapper.readValue(reader, DadosWrapper.class);
            Map<String, List<Medicao>> dados = wrapper.getDados();

            // Captura a lista de uptime_hours, se existir
            List<Medicao> listaUptime = dados.get("uptime_hours");

            Map<ChaveAgrupamento, List<Medicao>> agrupados = new HashMap<>();

            for (Map.Entry<String, List<Medicao>> entry : dados.entrySet()) {
                String metrica = entry.getKey();

                // pula a métrica uptime_hours, pois não é um dado a ser processado diretamente
                if ("uptime_hours".equals(metrica)) continue;

                List<Medicao> medicoes = entry.getValue();

                for (int i = 0; i < medicoes.size(); i++) {
                    Medicao medicao = medicoes.get(i);

                    if (listaUptime != null && i < listaUptime.size()) {
                        Double uptime = listaUptime.get(i).getValor();
                        medicao.setUptimeHours(uptime);
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
                List<Medicao> medicoes = entry.getValue();

                double somaGeral = 0.0;
                double somaPico = 0.0;
                double uptimeTotal = 0.0;
                double uptimePico = 0.0;
                int contador = 0;
                int contadorPico = 0;
                int contadorAcima95 = 0;

                for (Medicao m : medicoes) {
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

                double mediaGeral = contador > 0 ? somaGeral / contador : 0.0;
                double mediaPico = contadorPico > 0 ? somaPico / contadorPico : 0.0;

                resultados.add(new MediaDiariaResultado(
                        chave.getModelo(),
                        chave.getEmpresa(),
                        chave.getMetrica(),
                        chave.getData(),
                        mediaGeral,
                        mediaPico,
                        uptimeTotal,
                        uptimePico,
                        contadorAcima95
                ));
            }

            String keySaida = "media-diaria/" + empresa + "/" + data + ".json";
            byte[] json = objectMapper.writeValueAsBytes(resultados);

            s3Client.putObject(BUCKET_SAIDA, keySaida,
                    new java.io.ByteArrayInputStream(json), null);

            return "Processamento finalizado para " + sourceKey;

        } catch (Exception e) {
            context.getLogger().log("Erro: " + e.getMessage());
            return "Erro no processamento: " + e.getMessage();
        }
    }
}
