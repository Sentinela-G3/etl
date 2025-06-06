package sptech.school;

import com.fasterxml.jackson.annotation.JsonProperty;

public class MediaMensal {
    @JsonProperty("data") // Corresponde a "data" no JSON
    private String data;

    @JsonProperty("modelo") // Corresponde a "modelo" no JSON
    private String modelo;

    @JsonProperty("empresa") // Corresponde a "empresa" no JSON
    private String empresa;

    @JsonProperty("metrica") // Corresponde a "metrica" no JSON
    private String metrica;

    @JsonProperty("mediaGeral") // Corresponde a "mediaGeral" no JSON
    private double mediaGeral;

    @JsonProperty("mediaPico") // Corresponde a "mediaPico" no JSON
    private double mediaPico;

    @JsonProperty("uptimeTotal") // Corresponde a "uptimeTotal" no JSON
    private double uptimeTotal;

    @JsonProperty("uptimePico") // Corresponde a "uptimePico" no JSON
    private double uptimePico;

    // Se o JSON de entrada tem "contadorAcima95"
    @JsonProperty("contadorAcima95") // <<< DEVE CORRESPONDER AO JSON DE ENTRADA
    private int contagemValoresAcimaDe95; // O nome da variável Java pode ser diferente, mas a anotação é a chave

    // Construtor padrão (bom ter)
    public MediaMensal() {}

    // Getters e Setters
    public String getData() { return data; }
    public void setData(String data) { this.data = data; }

    public String getModelo() { return modelo; }
    public void setModelo(String modelo) { this.modelo = modelo; }

    public String getEmpresa() { return empresa; }
    public void setEmpresa(String empresa) { this.empresa = empresa; }

    public String getMetrica() { return metrica; }
    public void setMetrica(String metrica) { this.metrica = metrica; }

    public double getMediaGeral() { return mediaGeral; }
    public void setMediaGeral(double mediaGeral) { this.mediaGeral = mediaGeral; }

    public double getMediaPico() { return mediaPico; }
    public void setMediaPico(double mediaPico) { this.mediaPico = mediaPico; }

    public double getUptimeTotal() { return uptimeTotal; }
    public void setUptimeTotal(double uptimeTotal) { this.uptimeTotal = uptimeTotal; }

    public double getUptimePico() { return uptimePico; }
    public void setUptimePico(double uptimePico) { this.uptimePico = uptimePico; }

    public int getContagemValoresAcimaDe95() { return contagemValoresAcimaDe95; }
    public void setContagemValoresAcimaDe95(int contagemValoresAcimaDe95) {
        this.contagemValoresAcimaDe95 = contagemValoresAcimaDe95;
    }
}