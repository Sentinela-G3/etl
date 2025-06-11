package sptech.school;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ListObjectsV2Request;
import com.amazonaws.services.s3.model.ListObjectsV2Result;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;
import java.util.stream.Collectors;

public class Main implements RequestHandler<S3Event, String> {

    private static final String BUCKET_SAIDA = "sentinela-client-bucket";
    private static final String SEPARADOR_CHAVE = "|";
    private static final int MESES_A_MANTER = 48;
    private static final String NOME_ARQUIVO_SAIDA = "4-anos-media-recente.json";

    private final AmazonS3 s3Client = AmazonS3ClientBuilder.defaultClient();
    private final ObjectMapper objectMapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    @Override
    public String handleRequest(S3Event s3Event, Context context) {
        context.getLogger().log("LAMBDA_ANUAL_FINAL: Execução iniciada. RequestId: " + context.getAwsRequestId());

        try {
            if (s3Event == null || s3Event.getRecords() == null || s3Event.getRecords().isEmpty()) {
                context.getLogger().log("LAMBDA_ANUAL_FINAL: Evento S3 inválido ou sem registros.");
                return "Erro: Evento S3 inválido.";
            }

            String sourceBucket = s3Event.getRecords().get(0).getS3().getBucket().getName();
            String sourceKey = s3Event.getRecords().get(0).getS3().getObject().getKey();
            context.getLogger().log("LAMBDA_ANUAL_FINAL: Triggered by: s3://" + sourceBucket + "/" + sourceKey);

            String[] keyParts = sourceKey.split("/");
            if (keyParts.length < 2) {
                context.getLogger().log("LAMBDA_ANUAL_FINAL: Formato de chave S3 inválido: " + sourceKey);
                return "Erro: Formato de chave S3 inválido.";
            }
            String pastaRaiz = keyParts[0];
            String empresa = keyParts[1];
            context.getLogger().log("LAMBDA_ANUAL_FINAL: Processando para a empresa: " + empresa);

            String prefixo = pastaRaiz + "/" + empresa + "/";
            ListObjectsV2Request listReq = new ListObjectsV2Request().withBucketName(sourceBucket).withPrefix(prefixo);
            List<S3ObjectSummary> objetos = s3Client.listObjectsV2(listReq).getObjectSummaries();
            context.getLogger().log("LAMBDA_ANUAL_FINAL: Encontrados " + objetos.size() + " objetos no prefixo: " + prefixo);

            Map<String, List<MediaMensal>> dadosAgrupados = new HashMap<>();
            for (S3ObjectSummary obj : objetos) {
                if (obj.getKey().endsWith("/")) continue;

                context.getLogger().log("LAMBDA_ANUAL_FINAL: Lendo arquivo: " + obj.getKey());
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(s3Client.getObject(sourceBucket, obj.getKey()).getObjectContent()))) {
                    List<MediaMensal> mediasDoArquivo = objectMapper.readValue(reader, new TypeReference<>() {});

                    for (MediaMensal media : mediasDoArquivo) {
                        if (media.getEmpresa() == null || media.getModelo() == null || media.getData() == null) {
                            context.getLogger().log("WARN: Registro com campos nulos em " + obj.getKey() + ", pulando.");
                            continue;
                        }
                        String chave = media.getEmpresa() + SEPARADOR_CHAVE + media.getModelo();
                        dadosAgrupados.computeIfAbsent(chave, k -> new ArrayList<>()).add(media);
                    }
                } catch (Exception e) {
                    context.getLogger().log("ERROR: Falha ao ler ou processar o arquivo " + obj.getKey() + ": " + e.getMessage());
                }
            }

            context.getLogger().log("LAMBDA_ANUAL_FINAL: Total de " + dadosAgrupados.size() + " grupos (empresa|modelo) encontrados.");
            int arquivosSalvos = 0;

            for (Map.Entry<String, List<MediaMensal>> entry : dadosAgrupados.entrySet()) {
                String chave = entry.getKey();
                List<MediaMensal> todasAsMedias = entry.getValue();

                Set<String> mesesParaManter = todasAsMedias.stream()
                        .map(MediaMensal::getData)
                        .distinct()
                        .sorted(Comparator.reverseOrder())
                        .limit(MESES_A_MANTER)
                        .collect(Collectors.toSet());

                List<MediaMensal> mediasFiltradas = todasAsMedias.stream()
                        .filter(media -> mesesParaManter.contains(media.getData()))
                        .sorted(Comparator.comparing(MediaMensal::getData).thenComparing(MediaMensal::getMetrica))
                        .collect(Collectors.toList());

                context.getLogger().log(String.format("Processando chave '%s': %d registros filtrados para os %d meses mais recentes.",
                        chave, mediasFiltradas.size(), mesesParaManter.size()));

                String[] partesChave = chave.split("\\" + SEPARADOR_CHAVE);
                if (partesChave.length < 2) continue;
                String empresaDoModelo = partesChave[0];
                String modelo = partesChave[1];

                MediaAnualPorModelo relatorio = new MediaAnualPorModelo();
                relatorio.setEmpresa(empresaDoModelo);
                relatorio.setModelo(modelo);
                relatorio.setMediasMensais(mediasFiltradas);

                List<MediaAnualPorModelo> dadosConsolidados = Collections.singletonList(relatorio);

                try {
                    byte[] jsonBytes = objectMapper.writeValueAsBytes(dadosConsolidados);

                    String keySaida = String.format("%s/%s/%s",
                            empresaDoModelo,
                            modelo,
                            NOME_ARQUIVO_SAIDA);

                    ObjectMetadata metadata = new ObjectMetadata();
                    metadata.setContentLength(jsonBytes.length);
                    metadata.setContentType("application/json");

                    s3Client.putObject(BUCKET_SAIDA, keySaida, new ByteArrayInputStream(jsonBytes), metadata);
                    context.getLogger().log("Arquivo salvo com sucesso em: s3://" + BUCKET_SAIDA + "/" + keySaida);
                    arquivosSalvos++;

                } catch (Exception e) {
                    context.getLogger().log("ERROR: Falha ao salvar arquivo para a chave " + chave + ": " + e.getMessage());
                }
            }

            String mensagemFinal = String.format("Processamento para a empresa '%s' concluído. %d arquivos de relatório foram salvos.", empresa, arquivosSalvos);
            context.getLogger().log("LAMBDA_ANUAL_FINAL: " + mensagemFinal);
            return mensagemFinal;

        } catch (Exception e) {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            String errMsg = "LAMBDA_ANUAL_FINAL: Erro GERAL: " + e.getMessage() + "\nStack trace: " + sw;
            context.getLogger().log(errMsg);
            return "Erro no processamento: " + e.getMessage();
        }
    }
}