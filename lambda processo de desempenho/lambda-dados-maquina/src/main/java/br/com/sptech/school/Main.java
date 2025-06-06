package br.com.sptech.school;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.util.*;

public class Main implements RequestHandler<S3Event, String> {
    private final AmazonS3 s3Client = AmazonS3ClientBuilder.defaultClient();
    private static final String DESTINATION_BUCKET = "sentinela-trusted";
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String handleRequest(S3Event s3Event, Context context) {

        // Extraímos o nome do bucket de origem e a chave do arquivo JSON
        String sourceBucket = s3Event.getRecords().get(0).getS3().getBucket().getName();
        String sourceKey = s3Event.getRecords().get(0).getS3().getObject().getKey();

        try {
            // Leitura do arquivo JSON do bucket de origem
            InputStream s3InputStream = s3Client.getObject(sourceBucket, sourceKey).getObjectContent();

            // Conversão do JSON para uma lista de objetos Dados usando o Mapper
            DadosMapper mapper = new DadosMapper();
            List<Dados> dados = mapper.mapearPix(s3InputStream);

            // Geração do arquivo CSV a partir da lista de Pix usando o CsvWriter
            CsvWriter csvWriter = new CsvWriter();
            ByteArrayOutputStream csvOutputStream = csvWriter.writeCsv(dados);

            // Converte o ByteArrayOutputStream para InputStream para enviar ao bucket de destino
            InputStream csvInputStream = new ByteArrayInputStream(csvOutputStream.toByteArray());

            // Envio do CSV para o bucket de destino
            s3Client.putObject(DESTINATION_BUCKET, sourceKey.replace(".json", ".csv"), csvInputStream, null);

            return "Sucesso no processamento";
        } catch (Exception e) {
            // Tratamento de erros e log do contexto em caso de exceção
            context.getLogger().log("Erro: " + e.getMessage());
            return "Erro no processamento";
        }
    }
 }