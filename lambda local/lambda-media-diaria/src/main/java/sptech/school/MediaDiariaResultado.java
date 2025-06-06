package sptech.school;

public class MediaDiariaResultado {
    private String modelo;
    private String empresa;
    private String metrica;
    private String data;
    private double mediaGeral;
    private double mediaPico;
    private double uptimeTotal;
    private double uptimePico;
    private int acima95;

    public MediaDiariaResultado(String modelo, String empresa, String metrica, String data,
                                double mediaGeral, double mediaPico, double uptimeTotal,
                                double uptimePico, int acima95) {
        this.modelo = modelo;
        this.empresa = empresa;
        this.metrica = metrica;
        this.data = data;
        this.mediaGeral = mediaGeral;
        this.mediaPico = mediaPico;
        this.uptimeTotal = uptimeTotal;
        this.uptimePico = uptimePico;
        this.acima95 = acima95;
    }

    public String getModelo() { return modelo; }
    public String getEmpresa() { return empresa; }
    public String getMetrica() { return metrica; }
    public String getData() { return data; }
    public double getMediaGeral() { return mediaGeral; }
    public double getMediaPico() { return mediaPico; }
    public double getUptimeTotal() { return uptimeTotal; }
    public double getUptimePico() { return uptimePico; }
    public int getAcima95() { return acima95; }
}
