package sptech.school;

import com.fasterxml.jackson.annotation.JsonProperty;

public class MediaDiaria {
    private String modelo;
    private String empresa;
    private String metrica;
    private String data;
    private double mediaGeral;
    private double mediaPico;
    private double uptimeTotal;
    private double uptimePico;
    @JsonProperty("acima95")
    private int contadorAcima95;

    public MediaDiaria() {}

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

    public int getContadorAcima95() { return contadorAcima95; }
    public void setContadorAcima95(int contadorAcima95) { this.contadorAcima95 = contadorAcima95; }
}
