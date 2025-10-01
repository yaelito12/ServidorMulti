package com.mycompany.servidorMulti;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;

public class ServidorMulti {

    // Lista de clientes conectados
    static HashMap<String, UnCliente> clientes = new HashMap<>();

    public static void main(String[] args) throws IOException {
        // Servidor en el puerto 8080
        ServerSocket servidorSocket = new ServerSocket(8080);
        System.out.println("Servidor iniciado en el puerto 8080...");

        int contador = 0;

        while (true) {
            // Espera la conexión de un cliente
            Socket s = servidorSocket.accept();
            System.out.println("Nuevo cliente conectado: " + s);

            // Crea un manejador para el cliente
            UnCliente unCliente = new UnCliente(s);

            // Lo guarda en el HashMap con un ID
            clientes.put(Integer.toString(contador), unCliente);

            // Arranca el hilo para escuchar al cliente
            Thread hilo = new Thread(unCliente);
            hilo.start();

            contador++;
        }
    }
}