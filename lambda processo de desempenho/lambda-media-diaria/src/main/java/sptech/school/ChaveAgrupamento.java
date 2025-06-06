package sptech.school;

import java.util.Objects;

public class ChaveAgrupamento {
    private String modelo;
    private String empresa;
    private String metrica;
    private String data;

    public ChaveAgrupamento(String modelo, String empresa, String metrica, String data) {
        this.modelo = modelo;
        this.empresa = empresa;
        this.metrica = metrica;
        this.data = data;
    }

    public String getModelo() { return modelo; }
    public String getEmpresa() { return empresa; }
    public String getMetrica() { return metrica; }
    public String getData() { return data; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ChaveAgrupamento)) return false;
        ChaveAgrupamento that = (ChaveAgrupamento) o;
        return Objects.equals(modelo, that.modelo) &&
                Objects.equals(empresa, that.empresa) &&
                Objects.equals(metrica, that.metrica) &&
                Objects.equals(data, that.data);
    }

    @Override
    public int hashCode() {
        return Objects.hash(modelo, empresa, metrica, data);
    }
}
