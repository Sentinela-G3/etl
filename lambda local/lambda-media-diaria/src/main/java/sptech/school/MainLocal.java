package sptech.school;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class MainLocal {

    private static final String PASTA_ENTRADA = "dados-entrada"; // nome da pasta onde estão os JSONs de entrada
    private static final String PASTA_SAIDA = "saida-local";     // nome da pasta onde os arquivos processados serão salvos

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static void main(String[] args) {
        File pastaEntrada = new File(PASTA_ENTRADA);
        File[] arquivos = pastaEntrada.listFiles((dir, name) -> name.endsWith(".json"));

        if (arquivos == null || arquivos.length == 0) {
            System.out.println("Nenhum arquivo JSON encontrado em: " + PASTA_ENTRADA);
            return;
        }

        for (File arquivo : arquivos) {
            System.out.println("Processando: " + arquivo.getName());
            try {
                processarArquivo(arquivo);
            } catch (Exception e) {
                System.err.println("Erro ao processar " + arquivo.getName() + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private static void processarArquivo(File arquivo) throws IOException {
        String nomeArquivo = arquivo.getName(); // exemplo: acme_2025-06-01_medicoes.json
        String[] partes = nomeArquivo.split("_");
        if (partes.length < 3) {
            throw new IllegalArgumentException("Nome do arquivo inválido: " + nomeArquivo);
        }

        String empresa = partes[0];
        String data = partes[1];

        try (BufferedReader reader = new BufferedReader(new FileReader(arquivo))) {
            DadosWrapper wrapper = objectMapper.readValue(reader, DadosWrapper.class);
            Map<String, List<Medicao>> dados = wrapper.getDados();

            // Captura a lista de uptime_hours, se existir
            List<Medicao> listaUptime = dados.get("uptime_hours");

            Map<ChaveAgrupamento, List<Medicao>> agrupados = new HashMap<>();

            for (Map.Entry<String, List<Medicao>> entry : dados.entrySet()) {
                String metrica = entry.getKey();

                // pula a métrica uptime_hours, pois não é um dado a ser processado diretamente
                if (metrica.equals("uptime_hours")) continue;

                List<Medicao> medicoes = entry.getValue();

                for (int i = 0; i < medicoes.size(); i++) {
                    Medicao medicao = medicoes.get(i);

                    // Atribui o uptime correspondente, se houver
                    if (listaUptime != null && i < listaUptime.size()) {
                        Double uptime = listaUptime.get(i).getValor(); // uptime_hours também tem o valor em 'valor'
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

            salvarResultado(empresa, data, resultados);
        }
    }

    private static void salvarResultado(String empresa, String data, List<MediaDiariaResultado> resultados) throws IOException {
        Path pastaSaida = Paths.get(PASTA_SAIDA);
        Files.createDirectories(pastaSaida);

        File arquivoSaida = pastaSaida.resolve("media-diaria_" + empresa + "_" + data + ".json").toFile();
        objectMapper.writeValue(arquivoSaida, resultados);

        System.out.println("Arquivo gerado: " + arquivoSaida.getPath());
    }
}