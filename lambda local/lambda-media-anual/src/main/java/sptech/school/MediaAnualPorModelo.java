package sptech.school;

import java.util.List;

public class MediaAnualPorModelo {
    private String empresa;
    private String modelo;
    private List<MediaMensal> mediasMensais;

    public String getEmpresa() { return empresa; }
    public void setEmpresa(String empresa) { this.empresa = empresa; }

    public String getModelo() { return modelo; }
    public void setModelo(String modelo) { this.modelo = modelo; }

    public List<MediaMensal> getMediasMensais() { return mediasMensais; }
    public void setMediasMensais(List<MediaMensal> mediasMensais) { this.mediasMensais = mediasMensais; }
}
