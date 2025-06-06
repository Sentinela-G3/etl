package sptech.school;

import java.util.List;
import java.util.Map;

public class DadosWrapper {
    private Map<String, List<Medicao>> dados;

    public Map<String, List<Medicao>> getDados() { return dados; }
    public void setDados(Map<String, List<Medicao>> dados) { this.dados = dados; }
}
