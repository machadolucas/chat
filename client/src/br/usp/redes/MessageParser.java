package br.usp.redes;

import java.util.LinkedList;
import java.util.List;

public class MessageParser {

    public static List<Contact> parseContactsFromMessage(String message) {
        List<Contact> contacts = new LinkedList<>();

        String[] contactsData = message.split(";");
        //[nome/ip:porta][nome/ip:porta][nome/ip:porta]

        for (String contactData : contactsData) {
            Contact c = new Contact();
            String[] data = contactData.split("/"); // [nome],[ip:porta]
            String[] ipPort = data[1].split(":"); // [ip],[porta]

            c.setName(data[0]);
            c.setIp(ipPort[0]);
            c.setPort(ipPort[1]);

            contacts.add(c);

        }

        return contacts;
    }
}
