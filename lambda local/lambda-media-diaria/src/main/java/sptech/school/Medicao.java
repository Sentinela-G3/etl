package sptech.school;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Medicao {
    private int indice;
    private String timestamp;
    private Double valor;
    private String empresa;
    private String modelo;
    private Double uptimeHours; // opcional, para quando estiver presente

    public Medicao() {
    }

    public int getIndice() {
        return indice;
    }

    public void setIndice(int indice) {
        this.indice = indice;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public Double getValor() {
        return valor;
    }

    public void setValor(Double valor) {
        this.valor = valor;
    }

    public String getEmpresa() {
        return empresa;
    }

    public void setEmpresa(String empresa) {
        this.empresa = empresa;
    }

    public String getModelo() {
        return modelo;
    }

    public void setModelo(String modelo) {
        this.modelo = modelo;
    }

    public Double getUptimeHours() {
        return uptimeHours;
    }

    public void setUptimeHours(Double uptimeHours) {
        this.uptimeHours = uptimeHours;
    }
}
