package com.mycompany.servidormulti;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;

public class ServidorMulti {
    static HashMap<String, UnCliente> clientes = new HashMap<>();
    static HashMap<String, String> usuarios = new HashMap<>(); // nombreUsuario -> password
    
    public static void main(String[] args) {
        int puerto = 8080;
        
        try (ServerSocket servidorSocket = new ServerSocket(puerto)) {
            System.out.println("Servidor iniciado en el puerto " + puerto);
            System.out.println("Sistema de autenticación activado: 3 mensajes gratuitos");
            
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
    
    public static synchronized void registrarCliente(String nombre, UnCliente cliente) {
        clientes.put(nombre, cliente);
    }
    
    public static synchronized void registrarUsuario(String nombre, String password) {
        usuarios.put(nombre, password);
        System.out.println("Nuevo usuario registrado: " + nombre);
    }
    
    // Método para autenticar un usuario
    public static synchronized boolean autenticarUsuario(String nombre, String password) {
        return usuarios.containsKey(nombre) && usuarios.get(nombre).equals(password);
    }
}