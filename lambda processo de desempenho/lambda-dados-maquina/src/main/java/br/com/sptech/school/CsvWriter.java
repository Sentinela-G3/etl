package br.com.sptech.school;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class CsvWriter {
    public ByteArrayOutputStream writeCsv(List<Dados> dados) throws IOException {
        for (int i = 0; i < dados.size(); i++) {
            dados.get(i).formatarValor(dados.get(i).getValue());
        }

        Ordenacao.idSelSort(dados);

        // Criar um CSV em memória utilizando ByteArrayOutputStream
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));
        CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT.withHeader("id_maquina", "serial_number", "metric_type", "value", "timestamp"));

        // Processar e escrever cada objeto no CSV
        for (Dados p : dados) {
            csvPrinter.printRecord(p.getId(), p.getSerial(), p.getMetrica(), p.getValue(), p.getData());
        }

        // Fechar o CSV para garantir que todos os dados sejam escritos
        csvPrinter.flush();
        writer.close();

        // Retornar o ByteArrayOutp
        // utStream que contém o CSV gerado
        return outputStream;
    }
}
