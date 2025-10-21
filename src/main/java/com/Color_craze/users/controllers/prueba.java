package com.Color_craze.users.controllers;

import com.mongodb.client.*;

public class prueba {
    public static void mostrarColecciones() {
        try (MongoClient mongoClient = MongoClients.create(
            "mongodb+srv://emily:olecito3@color-craze.w6vlagc.mongodb.net/?retryWrites=true&w=majority&appName=Color-Craze")) {
            
            MongoDatabase database = mongoClient.getDatabase("color-craze");
            System.out.println("📂 Colecciones en la base de datos 'color-craze':");
            
            for (String name : database.listCollectionNames()) {
                System.out.println(" - " + name);
            }
        }
    }
}
