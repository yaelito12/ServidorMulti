package com.mycompany.servidormulti;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;

public class ServidorMulti {
    static HashMap<String, UnCliente> clientes = new HashMap<>();
    
    public static void main(String[] args) {
        int puerto = 8080;
        
        try (ServerSocket servidorSocket = new ServerSocket(puerto)) {
            System.out.println("Servidor iniciado en el puerto " + puerto);
            
            while (true) {
                Socket socket = servidorSocket.accept();
                
                UnCliente unCliente = new UnCliente(socket);
                Thread hilo = new Thread(unCliente);
                hilo.start();
            }
        } catch (IOException e) {
            System.out.println("Error en el servidor: " + e.getMessage());
        }
    }
    
    // Método para verificar si un nombre ya está en uso
    public static synchronized boolean nombreDisponible(String nombre) {
        return !clientes.containsKey(nombre);
    }
    
    // Método para registrar un cliente con su nombre
    public static synchronized void registrarCliente(String nombre, UnCliente cliente) {
        clientes.put(nombre, cliente);
    }
}