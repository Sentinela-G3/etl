package sptech.school;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ListObjectsV2Request;
import com.amazonaws.services.s3.model.ListObjectsV2Result;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.io.PrintWriter;
import java.util.*;

public class Main implements RequestHandler<S3Event, String> {

    private final AmazonS3 s3Client = AmazonS3ClientBuilder.defaultClient();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final String BUCKET_SAIDA = "clientbuckets3-spt";

    @Override
    public String handleRequest(S3Event s3Event, Context context) {
        context.getLogger().log("LAMBDA_ANUAL: Execução iniciada. RequestId: " + context.getAwsRequestId());

        try {
            if (s3Event == null || s3Event.getRecords() == null || s3Event.getRecords().isEmpty()) {
                context.getLogger().log("LAMBDA_ANUAL: Evento S3 inválido ou sem registros.");
                return "Erro: Evento S3 inválido.";
            }

            String sourceBucket = s3Event.getRecords().get(0).getS3().getBucket().getName();
            String sourceKey = s3Event.getRecords().get(0).getS3().getObject().getKey();
            context.getLogger().log("LAMBDA_ANUAL: Source Bucket: " + sourceBucket + ", Source Key: " + sourceKey);

            String[] keyParts = sourceKey.split("/");
            // Esperamos uma estrutura como "pasta_raiz/empresa/arquivo.json"
            // Ex: "media-mensal/acme-corp/2021-01.json"
            if (keyParts.length < 3) { // Precisa de pelo menos "pasta_raiz/empresa/arquivo"
                context.getLogger().log("LAMBDA_ANUAL: Formato de chave S3 inválido: " + sourceKey +
                        ". Esperava 'pasta_raiz/empresa/arquivo.json'.");
                return "Erro: Formato de chave S3 inválido para extrair empresa.";
            }

            String pastaRaiz = keyParts[0]; // Ex: "media-mensal"
            String empresa = keyParts[1];   // Ex: "acme-corp"
            // String nomeArquivoMes = keyParts[2]; // Ex: "2021-01.json" (não usado diretamente aqui, mas bom saber)

            context.getLogger().log("LAMBDA_ANUAL: Pasta Raiz extraída: " + pastaRaiz);
            context.getLogger().log("LAMBDA_ANUAL: Empresa extraída: " + empresa);

            // O prefixo para listar TODOS os arquivos ANO-MES.json para ESTA empresa
            String prefixoParaListarArquivosMensais = pastaRaiz + "/" + empresa + "/";
            context.getLogger().log("LAMBDA_ANUAL: Prefixo para listar arquivos ANO-MES: '" + prefixoParaListarArquivosMensais + "' no bucket '" + sourceBucket + "'");

            // Lista os arquivos de média mensal da empresa
            List<S3ObjectSummary> objetos = new ArrayList<>();
            ListObjectsV2Request listReq = new ListObjectsV2Request()
                    .withBucketName(sourceBucket)
                    .withPrefix(prefixoParaListarArquivosMensais);
            ListObjectsV2Result listResult;

            do {
                listResult = s3Client.listObjectsV2(listReq);
                objetos.addAll(listResult.getObjectSummaries());
                listReq.setContinuationToken(listResult.getNextContinuationToken());
            } while (listResult.isTruncated());

            context.getLogger().log("LAMBDA_ANUAL: Número total de arquivos ANO-MES encontrados (objetos.size()): " + objetos.size());

            if (objetos.isEmpty()) {
                context.getLogger().log("LAMBDA_ANUAL: Nenhum arquivo ANO-MES encontrado para a empresa '" + empresa + "'. Nada a processar para agregação anual.");
            }

            Map<String, List<MediaMensal>> mediasPorModelo = new HashMap<>();

            for (S3ObjectSummary obj : objetos) {
                if (obj.getKey().endsWith("/")) { // Ignorar pseudo-pastas
                    context.getLogger().log("LAMBDA_ANUAL: Ignorando objeto tipo pasta: " + obj.getKey());
                    continue;
                }
                context.getLogger().log("LAMBDA_ANUAL: Processando arquivo ANO-MES: " + obj.getKey());
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(s3Client.getObject(sourceBucket, obj.getKey()).getObjectContent()))) {

                    // Desserializa lista de MediaMensal
                    List<MediaMensal> mediasMensaisDoArquivo = objectMapper.readValue(reader, new TypeReference<List<MediaMensal>>() {});
                    context.getLogger().log("LAMBDA_ANUAL: Lidos " + mediasMensaisDoArquivo.size() + " registros MediaMensal de " + obj.getKey());

                    for (MediaMensal media : mediasMensaisDoArquivo) {
                        if (media.getEmpresa() == null || media.getModelo() == null || media.getData() == null) {
                            context.getLogger().log("LAMBDA_ANUAL: MediaMensal com empresa, modelo ou data nulos em " + obj.getKey() + ". Conteúdo do registro: " + (media != null ? media.toString() : "null") + ". Pulando este registro.");
                            continue;
                        }

                        String chave = media.getEmpresa() + "|" + media.getModelo();
                        mediasPorModelo.computeIfAbsent(chave, k -> new ArrayList<>()).add(media);
                    }
                } catch (Exception e) {
                    StringWriter sw = new StringWriter();
                    e.printStackTrace(new PrintWriter(sw));
                    context.getLogger().log("LAMBDA_ANUAL: Erro lendo/desserializando arquivo " + obj.getKey() + ": " + e.getMessage() + "\nStack trace: " + sw.toString());
                }
            }
            context.getLogger().log("LAMBDA_ANUAL: Tamanho do mapa mediasPorModelo (agrupado por empresa|modelo): " + mediasPorModelo.size());

            // Limitar a até 48 meses (objetos MediaMensal) por modelo, ordenando por data crescente
            for (Map.Entry<String, List<MediaMensal>> entryModelo : mediasPorModelo.entrySet()) {
                List<MediaMensal> listaMensaisDoModelo = entryModelo.getValue();
                context.getLogger().log("LAMBDA_ANUAL: Processando modelo: " + entryModelo.getKey() + ", número de MediaMensal antes da poda: " + listaMensaisDoModelo.size());

                // Ordena pela data "YYYY-MM" de forma crescente
                listaMensaisDoModelo.sort(Comparator.comparing(MediaMensal::getData));

                if (listaMensaisDoModelo.size() > 48) {
                    context.getLogger().log("LAMBDA_ANUAL: Podando lista de " + listaMensaisDoModelo.size() + " para 48 itens para o modelo: " + entryModelo.getKey());
                    listaMensaisDoModelo.subList(0, listaMensaisDoModelo.size() - 48).clear();
                }
                context.getLogger().log("LAMBDA_ANUAL: Modelo: " + entryModelo.getKey() + ", número de MediaMensal após poda: " + listaMensaisDoModelo.size());
            }

            List<MediaAnualPorModelo> resultadoFinal = new ArrayList<>();
            for (Map.Entry<String, List<MediaMensal>> entry : mediasPorModelo.entrySet()) {
                String[] partesDaChave = entry.getKey().split("\\|"); // Split por "|"
                if (partesDaChave.length < 2) { // Esperamos "empresa" e "modelo"
                    context.getLogger().log("LAMBDA_ANUAL: Chave do mapa mediasPorModelo malformada: '" + entry.getKey() + "'. Pulando esta entrada.");
                    continue;
                }
                MediaAnualPorModelo anual = new MediaAnualPorModelo();
                anual.setEmpresa(partesDaChave[0]);
                anual.setModelo(partesDaChave[1]);
                anual.setMediasMensais(entry.getValue()); // Pega a lista já ordenada e possivelmente podada
                resultadoFinal.add(anual);
            }
            context.getLogger().log("LAMBDA_ANUAL: Número de objetos MediaAnualPorModelo criados para o resultado final: " + resultadoFinal.size());

            String keySaida = empresa + "/4-anos/medias-4-anos.json";
            byte[] json = objectMapper.writeValueAsBytes(resultadoFinal);
            context.getLogger().log("LAMBDA_ANUAL: Tamanho do JSON de saída (bytes): " + json.length + ". Salvando em: s3://" + BUCKET_SAIDA + "/" + keySaida);

            com.amazonaws.services.s3.model.ObjectMetadata metadata = new com.amazonaws.services.s3.model.ObjectMetadata();
            metadata.setContentLength(json.length);
            metadata.setContentType("application/json");

            s3Client.putObject(BUCKET_SAIDA, keySaida,
                    new java.io.ByteArrayInputStream(json), metadata);

            context.getLogger().log("LAMBDA_ANUAL: Média anual para empresa '" + empresa + "' processada e salva com sucesso.");
            return "Média anual processada com sucesso para " + empresa;

        } catch (Exception e) {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            String errMsg = "LAMBDA_ANUAL: Erro GERAL no processamento da média anual: " + e.getMessage() + "\nStack trace: " + sw.toString();
            context.getLogger().log(errMsg);
            return "Erro no processamento da média anual: " + e.getMessage();
        }
    }
}