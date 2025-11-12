package com.example.ai.util;

import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOError;
import java.io.IOException;

@Component
public class VectorStoreUtils {
    private final VectorStore vectorStore;
    private final String path;

    public VectorStoreUtils(VectorStore vectorStore, @Value("${vectorstore.path}") String path) {
        this.vectorStore = vectorStore;
        this.path = path;
    }

    public void save() {
        try{
            SimpleVectorStore simpleVectorStore = (SimpleVectorStore) vectorStore;
            simpleVectorStore.save(new File( path));
        }catch (Exception e){
            e.printStackTrace();
        }
    }




}
