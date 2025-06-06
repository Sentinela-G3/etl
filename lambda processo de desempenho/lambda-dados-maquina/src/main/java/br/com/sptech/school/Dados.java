package br.com.sptech.school;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)

public class Dados {
    @JsonProperty("timestamp")
    private String data;
    @JsonProperty("value")
    private Double value;

    public void formatarValor(Double value) {
        this.value = Double.parseDouble(String.format("%.2f", value));
    }

    @JsonProperty("metric_type")
    private String metrica;
    @JsonProperty("serial_number")
    private String serial;
    @JsonProperty("id_maquina")
    private String id;

    public Dados() {
    }

    public Dados(String data, Double value, String metrica, String serial, String id) {
        this.id = id;
        this.serial = serial;
        this.metrica = metrica;
        this.value = value;
        this.data = data;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public Double getValue() {
        return value;
    }

    public void setValue(Double value) {
        this.value = value;
    }

    public String getMetrica() {
        return metrica;
    }

    public void setMetrica(String metrica) {
        this.metrica = metrica;
    }

    public String getSerial() {
        return serial;
    }

    public void setSerial(String serial) {
        this.serial = serial;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }
}
