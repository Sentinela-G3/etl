package sptech.school;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature; // Para JSON "bonito" (opcional)

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class ProcessamentoAnualLocal {

    private static final String PASTA_ENTRADA = "dados-entrada";
    private static final String PASTA_SAIDA = "saida-local";
    private static final String SEPARADOR_CHAVE = "|";
    private static final int MESES_A_MANTER = 48;
    private static final String SUFIXO_ARQUIVO_SAIDA = "_4-anos-media-recente.json";

    private static final ObjectMapper objectMapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    public static void main(String[] args) {
        File pastaEntrada = new File(PASTA_ENTRADA);
        File[] arquivos = pastaEntrada.listFiles((dir, name) -> name.endsWith(".json"));

        if (arquivos == null || arquivos.length == 0) {
            System.out.println("Nenhum arquivo JSON encontrado em: " + PASTA_ENTRADA);
            return;
        }

        Map<String, List<MediaMensal>> dadosAgrupados = new HashMap<>();

        for (File arquivo : arquivos) {
            System.out.println("Lendo arquivo: " + arquivo.getName());
            try {
                List<MediaMensal> mediasDoArquivo = objectMapper.readValue(arquivo, new TypeReference<>() {});
                System.out.println("Lidos " + mediasDoArquivo.size() + " registros de " + arquivo.getName());

                for (MediaMensal media : mediasDoArquivo) {
                    if (media.getEmpresa() == null || media.getModelo() == null || media.getData() == null) {
                        System.err.println("Registro com campos nulos em " + arquivo.getName() + ", pulando");
                        continue;
                    }
                    String chave = media.getEmpresa() + SEPARADOR_CHAVE + media.getModelo();
                    dadosAgrupados.computeIfAbsent(chave, k -> new ArrayList<>()).add(media);
                }
            } catch (Exception e) {
                System.err.println("Erro ao processar arquivo " + arquivo.getName() + ": " + e.getMessage());
                e.printStackTrace();
            }
        }

        Map<String, List<MediaAnualPorModelo>> saidaPorEmpresaEModelo = new HashMap<>();

        for (Map.Entry<String, List<MediaMensal>> entry : dadosAgrupados.entrySet()) {
            String chave = entry.getKey();
            List<MediaMensal> todasAsMedias = entry.getValue();

            List<String> mesesUnicos = todasAsMedias.stream()
                    .map(MediaMensal::getData)
                    .distinct()
                    .sorted(Comparator.reverseOrder())
                    .limit(MESES_A_MANTER)
                    .collect(Collectors.toList());

            Set<String> mesesParaManter = new HashSet<>(mesesUnicos);

            List<MediaMensal> mediasFiltradas = todasAsMedias.stream()
                    .filter(media -> mesesParaManter.contains(media.getData()))
                    .sorted(Comparator.comparing(MediaMensal::getData)
                            .thenComparing(MediaMensal::getMetrica))
                    .collect(Collectors.toList());

            System.out.printf("Processando chave '%s': %d registros filtrados para os %d meses mais recentes.%n",
                    chave, mediasFiltradas.size(), mesesParaManter.size());

            String[] partesChave = chave.split("\\" + SEPARADOR_CHAVE);
            if (partesChave.length < 2) continue;
            String empresa = partesChave[0];
            String modelo = partesChave[1];

            MediaAnualPorModelo relatorio = new MediaAnualPorModelo();
            relatorio.setEmpresa(empresa);
            relatorio.setModelo(modelo);
            relatorio.setMediasMensais(mediasFiltradas);

            saidaPorEmpresaEModelo.computeIfAbsent(chave, k -> new ArrayList<>()).add(relatorio);
        }

        for (Map.Entry<String, List<MediaAnualPorModelo>> entry : saidaPorEmpresaEModelo.entrySet()) {
            String chave = entry.getKey();
            String[] partesChave = chave.split("\\" + SEPARADOR_CHAVE);
            String empresa = partesChave[0];
            String modelo = partesChave[1];

            List<MediaAnualPorModelo> dadosConsolidados = entry.getValue();

            try {
                byte[] json = objectMapper.writeValueAsBytes(dadosConsolidados);

                File pastaEmpresa = Paths.get(PASTA_SAIDA, empresa, modelo).toFile();
                if (!pastaEmpresa.exists()) pastaEmpresa.mkdirs();

                File arquivoSaida = new File(pastaEmpresa, empresa + "_" + modelo + SUFIXO_ARQUIVO_SAIDA);

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