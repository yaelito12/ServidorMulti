package com.mycompany.servidormulti;

import java.sql.*;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;

public class BaseDatos {
    private static final String DB_URL = "jdbc:sqlite:chat.db";
    
    public void inicializar() {
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement()) {
            
            // Tabla de usuarios
            stmt.execute("CREATE TABLE IF NOT EXISTS usuarios (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "nombre TEXT UNIQUE NOT NULL," +
                    "password TEXT NOT NULL," +
                    "fecha_registro TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
            
            // Tabla de bloqueos
            stmt.execute("CREATE TABLE IF NOT EXISTS bloqueados (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "usuario_que_bloquea TEXT NOT NULL," +
                    "usuario_bloqueado TEXT NOT NULL," +
                    "fecha_bloqueo TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "UNIQUE(usuario_que_bloquea, usuario_bloqueado)," +
                    "FOREIGN KEY(usuario_que_bloquea) REFERENCES usuarios(nombre)," +
                    "FOREIGN KEY(usuario_bloqueado) REFERENCES usuarios(nombre))");
            
            // Tabla de estadísticas de juego
            stmt.execute("CREATE TABLE IF NOT EXISTS estadisticas_gato (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "jugador TEXT NOT NULL," +
                    "victorias INTEGER DEFAULT 0," +
                    "empates INTEGER DEFAULT 0," +
                    "derrotas INTEGER DEFAULT 0," +
                    "puntos INTEGER DEFAULT 0," +
                    "UNIQUE(jugador)," +
                    "FOREIGN KEY(jugador) REFERENCES usuarios(nombre))");
            
            // Tabla de historial de partidas entre jugadores
            stmt.execute("CREATE TABLE IF NOT EXISTS historial_partidas (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "jugador1 TEXT NOT NULL," +
                    "jugador2 TEXT NOT NULL," +
                    "ganador TEXT," +
                    "fecha_partida TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "FOREIGN KEY(jugador1) REFERENCES usuarios(nombre)," +
                    "FOREIGN KEY(jugador2) REFERENCES usuarios(nombre))");
            
            System.out.println("Base de datos inicializada correctamente");
        } catch (SQLException e) {
            System.err.println("Error inicializando BD: " + e.getMessage());
        }
    }
    
    public synchronized void guardarUsuario(String nombre, String password) {
        String sql = "INSERT INTO usuarios (nombre, password) VALUES (?, ?)";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, nombre);
            pstmt.setString(2, password);
            pstmt.executeUpdate();
         
            inicializarEstadisticas(nombre);
        } catch (SQLException e) {
            System.err.println("Error guardando usuario: " + e.getMessage());
        }
    }
    
    private void inicializarEstadisticas(String jugador) {
        String sql = "INSERT OR IGNORE INTO estadisticas_gato (jugador, victorias, empates, derrotas, puntos) VALUES (?, 0, 0, 0, 0)";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, jugador);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error inicializando estadísticas: " + e.getMessage());
        }
    }
    
    public HashMap<String, String> cargarTodosLosUsuarios() {
        HashMap<String, String> usuarios = new HashMap<>();
        String sql = "SELECT nombre, password FROM usuarios";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                String nombre = rs.getString("nombre");
                usuarios.put(nombre, rs.getString("password"));
                
                // Asegurar que existan estadísticas para usuarios antiguos
                inicializarEstadisticas(nombre);
            }
        } catch (SQLException e) {
            System.err.println("Error cargando usuarios: " + e.getMessage());
        }
        return usuarios;
    }
    
    public synchronized boolean bloquearUsuario(String usuarioActual, String usuarioABloquear) {
        if (estaBloqueado(usuarioActual, usuarioABloquear)) {
            return false;
        }
        
        String sql = "INSERT INTO bloqueados (usuario_que_bloquea, usuario_bloqueado) VALUES (?, ?)";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, usuarioActual);
            pstmt.setString(2, usuarioABloquear);
            pstmt.executeUpdate();
            System.out.println(usuarioActual + " bloqueó a " + usuarioABloquear);
            return true;
        } catch (SQLException e) {
            System.err.println("Error bloqueando usuario: " + e.getMessage());
            return false;
        }
    }
    
    public synchronized boolean desbloquearUsuario(String usuarioActual, String usuarioADesbloquear) {
        if (!estaBloqueado(usuarioActual, usuarioADesbloquear)) {
            return false;
        }
        
        String sql = "DELETE FROM bloqueados WHERE usuario_que_bloquea = ? AND usuario_bloqueado = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, usuarioActual);
            pstmt.setString(2, usuarioADesbloquear);
            pstmt.executeUpdate();
            System.out.println(usuarioActual + " desbloqueó a " + usuarioADesbloquear);
            return true;
        } catch (SQLException e) {
            System.err.println("Error desbloqueando usuario: " + e.getMessage());
            return false;
        }
    }
    
    public boolean estaBloqueado(String usuarioOrigen, String usuarioDestino) {
        String sql = "SELECT COUNT(*) FROM bloqueados WHERE usuario_que_bloquea = ? AND usuario_bloqueado = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, usuarioOrigen);
            pstmt.setString(2, usuarioDestino);
            ResultSet rs = pstmt.executeQuery();
            
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            System.err.println("Error verificando bloqueo: " + e.getMessage());
        }
        return false;
    }
    
    public List<String> obtenerBloqueados(String usuario) {
        List<String> bloqueados = new ArrayList<>();
        String sql = "SELECT usuario_bloqueado FROM bloqueados WHERE usuario_que_bloquea = ? ORDER BY fecha_bloqueo DESC";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, usuario);
            ResultSet rs = pstmt.executeQuery();
            
            while (rs.next()) {
                bloqueados.add(rs.getString("usuario_bloqueado"));
            }
        } catch (SQLException e) {
            System.err.println("Error obteniendo bloqueados: " + e.getMessage());
        }
        return bloqueados;
    }
            }
