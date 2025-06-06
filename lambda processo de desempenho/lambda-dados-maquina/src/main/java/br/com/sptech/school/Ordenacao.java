package br.com.sptech.school;

import java.util.List;

public class Ordenacao {
    public static void idSelSort(List<Dados> v) {
        for (int i = 0; i < v.size(); i++) {
            int menorIndice = i;
            for (int j = i + 1; j < v.size(); j++) {
                String idAtual = v.get(j).getId();
                String idMenor = v.get(menorIndice).getId();
                if (idAtual.compareTo(idMenor) < 0) {
                    menorIndice = j;
                }
            }
            Dados temp = v.get(i);
            v.set(i, v.get(menorIndice));
            v.set(menorIndice, temp);
        }
    }
}
