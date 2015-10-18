package br.usp.redes;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

public class FilesInterpreter {

    public static List<String> getFileAsStringList(String filePath) {
        final List<String> lines = new LinkedList<>();

        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {

            String sCurrentLine;
            while ((sCurrentLine = br.readLine()) != null) {
                lines.add(sCurrentLine);
            }

        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }

        return lines;
    }

    public static HashMap<String, List<String>> getClientsAndContacts() {
        final HashMap<String, List<String>> map = new HashMap<>();

        final List<String> toParse = getFileAsStringList("clients.txt");

        //Parseia as linhas no formato que primeiro vem o nome do cliente, separado por ";" de seus contatos.
        //Exemplo: "cliente;contato1;contato2;contato3"
        if (toParse != null) {
            for (String s : toParse) {
                final String[] clientAndContacts = s.split(";");
                final List<String> contacts = new LinkedList<>();
                contacts.addAll(Arrays.asList(clientAndContacts).subList(1, clientAndContacts.length));
                map.put(clientAndContacts[0], contacts);
            }
        }

        return map;
    }
}
