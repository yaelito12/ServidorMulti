package com.mycompany.servidormulti;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;

public class ServidorMulti {
    static HashMap<String, UnCliente> clientes = new HashMap<>();
    static HashMap<String, String> usuarios = new HashMap<>(); // nombreUsuario -> password
    static final String ARCHIVO_USUARIOS = "usuarios.txt";
    
    public static void main(String[] args) {
        int puerto = 8080;
      
        cargarUsuariosDelArchivo();
        
        try (ServerSocket servidorSocket = new ServerSocket(puerto)) {
            System.out.println("Servidor iniciado en el puerto " + puerto);
            System.out.println("Sistema de autenticación activado: 3 mensajes gratuitos");
            System.out.println("Usuarios cargados: " + usuarios.size());
            
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
   
    public static synchronized boolean nombreDisponible(String nombre) {
        return !clientes.containsKey(nombre);
    }
    
    public static synchronized void registrarCliente(String nombre, UnCliente cliente) {
        clientes.put(nombre, cliente);
    }
    
    public static synchronized void registrarUsuario(String nombre, String password) {
        usuarios.put(nombre, password);
        guardarUsuarioEnArchivo(nombre, password);
        System.out.println("Nuevo usuario registrado: " + nombre);
    }

    public static synchronized boolean autenticarUsuario(String nombre, String password) {
        return usuarios.containsKey(nombre) && usuarios.get(nombre).equals(password);
    }
  
    private static void cargarUsuariosDelArchivo() {
        try {
            Path path = Paths.get(ARCHIVO_USUARIOS);
            
            // Crear archivo si no existe
            if (Files.notExists(path)) {
                Files.createFile(path);
                System.out.println("Archivo de usuarios creado: " + ARCHIVO_USUARIOS);
                return;
            }
            
            // Leer y cargar usuarios
            try (BufferedReader br = new BufferedReader(new FileReader(ARCHIVO_USUARIOS))) {
                String linea;
                while ((linea = br.readLine()) != null) {
                    if (!linea.trim().isEmpty()) {
                        String[] datos = linea.split(":");
                        if (datos.length == 2) {
                            usuarios.put(datos[0], datos[1]);
                        }
                    }
                }
            }
            System.out.println("Usuarios cargados del archivo: " + usuarios.size());
            
        } catch (IOException e) {
            System.err.println("Error al cargar usuarios: " + e.getMessage());
        }
    }
    
    private static void guardarUsuarioEnArchivo(String nombre, String password) {
        try (FileWriter fw = new FileWriter(ARCHIVO_USUARIOS, true);
             BufferedWriter bw = new BufferedWriter(fw)) {
            bw.write(nombre + ":" + password);
            bw.newLine();
        } catch (IOException e) {
            System.err.println("Error al guardar usuario en archivo: " + e.getMessage());
        }
    }
}