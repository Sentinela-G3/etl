package sptech.school;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public class MediaMensalLocalProcessor {

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

        Map<String, List<MediaDiaria>> mediasPorChave = new HashMap<>();

        for (File arquivo : arquivos) {
            System.out.println("Lendo arquivo: " + arquivo.getName());

            String nomeArquivo = arquivo.getName();
            String nomeSemExtensao = nomeArquivo.replace(".json", "");
            String[] partesNome = nomeSemExtensao.split("_");

            if (partesNome.length < 4) {
                System.out.println("Nome de arquivo inválido (esperado: media_<empresa>_<modelo>_<YYYY-MM-DD>.json): " + nomeArquivo);
                continue;
            }

            String empresa = partesNome[1];
            String modelo = partesNome[2];
            String dataCompleta = partesNome[3];

            if (dataCompleta.length() < 7) {
                System.out.println("Data no nome do arquivo está malformada: " + nomeArquivo);
                continue;
            }

            String anoMes = dataCompleta.substring(0, 7);

            // Depois de extrair empresa e anoMes, você lê o JSON e processa normalmente
            try (BufferedReader reader = Files.newBufferedReader(arquivo.toPath())) {
                List<MediaDiaria> mediasDiarias = objectMapper.readValue(reader, new TypeReference<List<MediaDiaria>>() {});
                if (mediasDiarias.isEmpty()) {
                    System.out.println("Arquivo vazio ou inválido: " + arquivo.getName());
                    continue;
                }

                for (MediaDiaria media : mediasDiarias) {
                    if (media.getEmpresa() == null || media.getModelo() == null || media.getMetrica() == null) {
                        System.out.println("Registro inválido no arquivo " + nomeArquivo);
                        continue;
                    }

                    String chave = media.getEmpresa() + "|" + media.getModelo() + "|" + media.getMetrica() + "|" + anoMes;
                    mediasPorChave.computeIfAbsent(chave, k -> new ArrayList<>()).add(media);
                }
            } catch (Exception e) {
                System.err.println("Erro processando arquivo " + arquivo.getName() + ": " + e.getMessage());
                e.printStackTrace();
            }
        }

        // Novo mapa para agrupar por empresa + anoMes
        Map<String, List<MediaMensal>> resultadoPorEmpresaMes = new HashMap<>();

        for (Map.Entry<String, List<MediaDiaria>> entry : mediasPorChave.entrySet()) {
            String[] partesChave = entry.getKey().split("\\|");
            if (partesChave.length < 4) continue;

            String empresa = partesChave[0];
            String modelo = partesChave[1];
            String metrica = partesChave[2];
            String anoMes = partesChave[3];

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

            double mediaGeralCalc = contador > 0 ? somaGeral / contador : 0;
            double mediaPicoCalc = contadorPico > 0 ? somaPico / contadorPico : 0;

            MediaMensal mediaMensal = new MediaMensal();
            mediaMensal.setEmpresa(empresa);
            mediaMensal.setModelo(modelo);
            mediaMensal.setMetrica(metrica);
            mediaMensal.setData(anoMes);
            mediaMensal.setMediaGeral(mediaGeralCalc);
            mediaMensal.setMediaPico(mediaPicoCalc);
            mediaMensal.setUptimeTotal(uptimeTotal);
            mediaMensal.setUptimePico(uptimePico);
            mediaMensal.setContadorAcima95(contadorAcima95);

            // Agrupa por empresa + modelo + mês
            String chaveEmpresaMes = empresa + "|" + modelo + "|" + anoMes;
            resultadoPorEmpresaMes.computeIfAbsent(chaveEmpresaMes, k -> new ArrayList<>()).add(mediaMensal);
        }

        // Salvar um JSON por empresa + modelo + mês
        for (Map.Entry<String, List<MediaMensal>> entry : resultadoPorEmpresaMes.entrySet()) {
            String[] partes = entry.getKey().split("\\|");
            String empresa = partes[0];
            String modelo = partes[1];
            String anoMes = partes[2];
            List<MediaMensal> dados = entry.getValue();

            String nomeArquivoSaida = PASTA_SAIDA + "/" + empresa + "_" + modelo + "_" + anoMes + "_media-mensal.json";
            try {
                objectMapper.writeValue(new File(nomeArquivoSaida), dados);
                System.out.println("Média mensal salva: " + nomeArquivoSaida);
            } catch (IOException e) {
                System.err.println("Erro ao salvar média mensal: " + e.getMessage());
            }
        }
    }
}
