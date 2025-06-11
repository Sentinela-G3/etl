package sptech.school;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.lambda.runtime.events.models.s3.S3EventNotification.S3EventNotificationRecord;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Main implements RequestHandler<S3Event, String> {

    private static final AmazonS3 s3Client = AmazonS3ClientBuilder.defaultClient();
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final String PASTA_SAIDA_PREFIX = "media-mensal/";

    @Override
    public String handleRequest(S3Event s3Event, Context context) {
        context.getLogger().log("Iniciando processamento de média mensal...");

        try {
            // ETAPA 1: Identificar o mês a ser recalculado a partir do arquivo que acionou o evento.
            S3EventNotificationRecord record = s3Event.getRecords().get(0);
            String bucketName = record.getS3().getBucket().getName();
            String key = URLDecoder.decode(record.getS3().getObject().getKey(), StandardCharsets.UTF_8.toString());

            if (!key.startsWith("media-diaria/")) {
                return "Evento ignorado: arquivo não está na pasta 'media-diaria/'.";
            }

            String nomeArquivo = key.substring(key.lastIndexOf('/') + 1);
            String pastaEntrada = key.substring(0, key.lastIndexOf('/') + 1);
            String[] partesNome = nomeArquivo.replace(".json", "").split("_");

            if (partesNome.length < 4) {
                return "ERRO: Nome de arquivo inválido no gatilho: " + nomeArquivo;
            }
            String anoMesAlvo = partesNome[3].substring(0, 7);
            context.getLogger().log("Gatilho recebido. Mês a ser recalculado: " + anoMesAlvo);

            // ETAPA 2: Ler TODOS os arquivos da pasta de entrada que correspondem ao mês alvo.
            Map<String, List<MediaDiaria>> mediasPorChave = new HashMap<>();
            ListObjectsV2Request req = new ListObjectsV2Request().withBucketName(bucketName).withPrefix(pastaEntrada);
            ListObjectsV2Result result;

            context.getLogger().log("Buscando todos os arquivos para o mês " + anoMesAlvo + "...");
            do {
                result = s3Client.listObjectsV2(req);
                for (S3ObjectSummary summary : result.getObjectSummaries()) {
                    String arquivoAtualKey = summary.getKey();

                    if (arquivoAtualKey.endsWith(".json") && arquivoAtualKey.contains("_" + anoMesAlvo)) {
                        context.getLogger().log("Lendo dados do arquivo: " + arquivoAtualKey);
                        S3Object s3Object = s3Client.getObject(bucketName, arquivoAtualKey);
                        try (InputStream inputStream = s3Object.getObjectContent()) {
                            List<MediaDiaria> mediasDiarias = objectMapper.readValue(inputStream, new TypeReference<List<MediaDiaria>>() {});
                            for (MediaDiaria media : mediasDiarias) {
                                String chave = media.getEmpresa() + "|" + media.getModelo() + "|" + media.getMetrica() + "|" + anoMesAlvo;
                                mediasPorChave.computeIfAbsent(chave, k -> new ArrayList<>()).add(media);
                            }
                        }
                    }
                }
                req.setContinuationToken(result.getNextContinuationToken());
            } while (result.isTruncated());
            context.getLogger().log("Leitura de dados concluída. Total de grupos para cálculo: " + mediasPorChave.size());

            // ETAPA 3: Calcular as médias mensais.
            Map<String, List<MediaMensal>> resultadoPorChaveSaida = new HashMap<>();
            for (Map.Entry<String, List<MediaDiaria>> entry : mediasPorChave.entrySet()) {
                String[] partesChave = entry.getKey().split("\\|");
                String empresa = partesChave[0];
                String modelo = partesChave[1];
                String metrica = partesChave[2];
                String dataCalculo = partesChave[3];

                List<MediaDiaria> dias = entry.getValue();
                double somaGeral = 0.0, somaPico = 0.0, uptimeTotal = 0.0, uptimePico = 0.0;
                int contador = 0, contadorPico = 0, contadorAcima95 = 0;

                for (MediaDiaria dia : dias) {
                    somaGeral += dia.getMediaGeral();
                    somaPico += dia.getMediaPico();
                    uptimeTotal += dia.getUptimeTotal();
                    uptimePico += dia.getUptimePico();
                    contador++;
                    contadorAcima95 += dia.getContadorAcima95();
                    if (dia.getMediaPico() > 0) contadorPico++;
                }

                MediaMensal mediaMensal = new MediaMensal();
                mediaMensal.setEmpresa(empresa);
                mediaMensal.setModelo(modelo);
                mediaMensal.setMetrica(metrica);
                mediaMensal.setData(dataCalculo);
                mediaMensal.setMediaGeral((contador > 0) ? somaGeral / contador : 0);
                mediaMensal.setMediaPico((contadorPico > 0) ? somaPico / contadorPico : 0);
                mediaMensal.setUptimeTotal(uptimeTotal);
                mediaMensal.setUptimePico(uptimePico);
                mediaMensal.setContadorAcima95(contadorAcima95);

                String chaveSaida = empresa + "|" + modelo + "|" + dataCalculo;
                resultadoPorChaveSaida.computeIfAbsent(chaveSaida, k -> new ArrayList<>()).add(mediaMensal);
            }

            // ETAPA 4: Salvar os arquivos de resultado, sobrescrevendo os existentes.
            int arquivosSalvos = 0;
            for (Map.Entry<String, List<MediaMensal>> entry : resultadoPorChaveSaida.entrySet()) {
                String[] partes = entry.getKey().split("\\|");
                String empresa = partes[0];
                String modelo = partes[1];
                String dataFinal = partes[2];

                List<MediaMensal> dados = entry.getValue();

                String chaveSaidaS3 = String.format("%s%s/%s/%s.json", PASTA_SAIDA_PREFIX, empresa, modelo, dataFinal);

                byte[] bytes = objectMapper.writeValueAsBytes(dados);
                ObjectMetadata metadata = new ObjectMetadata();
                metadata.setContentLength(bytes.length);
                metadata.setContentType("application/json");

                s3Client.putObject(bucketName, chaveSaidaS3, new ByteArrayInputStream(bytes), metadata);

                context.getLogger().log("Média mensal salva/atualizada em: s3://" + bucketName + "/" + chaveSaidaS3);
                arquivosSalvos++;
            }

            String resultadoFinal = "Recálculo para " + anoMesAlvo + " concluído. " + arquivosSalvos + " arquivos de média mensal foram atualizados.";
            context.getLogger().log(resultadoFinal);
            return resultadoFinal;

        } catch (Exception e) {
            context.getLogger().log("[ERRO FATAL] " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Falha ao processar média mensal", e);
        }
    }
}