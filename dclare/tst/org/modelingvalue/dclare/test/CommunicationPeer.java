package org.modelingvalue.dclare.test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class CommunicationPeer {
    public static void main(String[] args) throws IOException {
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(System.in));
        String         line           = bufferedReader.readLine();
        int            rt             = Integer.parseInt(line);

        System.exit(rt);
    }
}