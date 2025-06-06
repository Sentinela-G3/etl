package sptech.school;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ProcessamentoAnualLocal {

    private static final String PASTA_ENTRADA = "dados-entrada";
    private static final String PASTA_SAIDA = "saida-local";
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static void main(String[] args) {
        File pastaEntrada = new File(PASTA_ENTRADA);
        File[] arquivos = pastaEntrada.listFiles((dir, name) -> name.endsWith(".json"));

        if (arquivos == null || arquivos.length == 0) {
            System.out.println("Nenhum arquivo JSON encontrado em: " + PASTA_ENTRADA);
            return;
        }

        // Mapa para agrupar dados: empresa|modelo -> lista MediaMensal
        Map<String, List<MediaMensal>> mediasPorEmpresaModelo = new HashMap<>();

        for (File arquivo : arquivos) {
            System.out.println("Lendo arquivo: " + arquivo.getName());
            try {
                // Extrair empresa do nome do arquivo (exemplo: "empresa_algumaCoisa.json")
                String nomeArquivo = arquivo.getName();
                String empresa = nomeArquivo.split("_")[0];
                if (empresa == null || empresa.isEmpty()) {
                    System.err.println("Não foi possível extrair empresa do arquivo: " + nomeArquivo);
                    continue;
                }

                // Ler dados do arquivo
                List<MediaMensal> mediasDoArquivo = objectMapper.readValue(
                        arquivo, new TypeReference<List<MediaMensal>>() {});

                System.out.println("Lidos " + mediasDoArquivo.size() + " registros de " + arquivo.getName());

                // Agrupar no mapa geral
                for (MediaMensal media : mediasDoArquivo) {
                    if (media.getEmpresa() == null || media.getModelo() == null || media.getData() == null) {
                        System.err.println("Registro com campos nulos em " + arquivo.getName() + ", pulando");
                        continue;
                    }
                    String chave = media.getEmpresa() + "|" + media.getModelo();
                    mediasPorEmpresaModelo.computeIfAbsent(chave, k -> new ArrayList<>()).add(media);
                }
            } catch (Exception e) {
                System.err.println("Erro ao processar arquivo " + arquivo.getName() + ": " + e.getMessage());
                e.printStackTrace();
            }
        }

        // Agora temos todos os dados agrupados, vamos gerar a lista final de MediaAnualPorModelo para cada empresa
        // Mapa empresa -> lista de MediaAnualPorModelo
        Map<String, List<MediaAnualPorModelo>> saidaPorEmpresa = new HashMap<>();

        for (Map.Entry<String, List<MediaMensal>> entry : mediasPorEmpresaModelo.entrySet()) {
            String chave = entry.getKey();
            List<MediaMensal> listaMensais = entry.getValue();

            // Ordenar por data e limitar a 48 meses
            listaMensais.sort(Comparator.comparing(MediaMensal::getData));
            if (listaMensais.size() > 48) {
                listaMensais.subList(0, listaMensais.size() - 48).clear();
            }

            String[] partesChave = chave.split("\\|");
            if (partesChave.length < 2) continue;
            String empresa = partesChave[0];
            String modelo = partesChave[1];

            MediaAnualPorModelo anual = new MediaAnualPorModelo();
            anual.setEmpresa(empresa);
            anual.setModelo(modelo);
            anual.setMediasMensais(listaMensais);

            saidaPorEmpresa.computeIfAbsent(empresa, k -> new ArrayList<>()).add(anual);
        }

        // Salvar arquivos por empresa
        for (Map.Entry<String, List<MediaAnualPorModelo>> entry : saidaPorEmpresa.entrySet()) {
            String empresa = entry.getKey();
            List<MediaAnualPorModelo> listaAnual = entry.getValue();

            try {
                // Serializar resultado
                byte[] json = objectMapper.writeValueAsBytes(listaAnual);

                // Criar pasta saída empresa
                File pastaEmpresa = Paths.get(PASTA_SAIDA, empresa).toFile();
                if (!pastaEmpresa.exists()) pastaEmpresa.mkdirs();

                // Nome do arquivo consolidado
                File arquivoSaida = new File(pastaEmpresa, empresa + "_4-anos_medias-4-anos.json");

                try (FileOutputStream fos = new FileOutputStream(arquivoSaida)) {
                    fos.write(json);
                }

                System.out.println("Arquivo consolidado salvo em: " + arquivoSaida.getPath());

            } catch (Exception e) {
                System.err.println("Erro ao salvar arquivo da empresa " + empresa + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
}

