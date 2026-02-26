package com.project.skin_me.service.chatAI;

import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;
import com.project.skin_me.model.Product;
import com.project.skin_me.service.product.IProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeminiService {

    private final Client client;
    private final IProductService productService;

    public String askGemini(String prompt){
        List<Product> relevantProducts = findRelevantProducts(prompt);
        String productContext = formatProductsForGemini(relevantProducts);
        String finalPrompt = createAssistantPrompt(productContext, prompt);

        GenerateContentResponse response =
                client.models.generateContent(
                        "gemini-2.5-flash",
                        finalPrompt,
                        null);

        return response.text();
    }

    private List<Product> findRelevantProducts(String prompt) {
        String lowerPrompt = prompt.toLowerCase();

        if (lowerPrompt.contains("cleanser")) {
            return productService.getProductsByProductType("Cleanser");
        }
        if (lowerPrompt.contains("serum")) {
            return productService.getProductsByProductType("Serum");
        }
        if (lowerPrompt.contains("moisturizer")) {
            return productService.getProductsByProductType("Moisturizer");
        }
        if (lowerPrompt.contains("sunscreen") || lowerPrompt.contains("spf")) {
            return productService.getProductsByProductType("Sunscreen");
        }

        if (lowerPrompt.contains("popular") || lowerPrompt.contains("bestseller")) {
            return productService.getPopularProducts();
        }
        try {
            return productService.getAllProducts().stream().limit(10).toList();
        } catch (Exception e) {
            log.error("Error fetching all products for fallback", e);
            return List.of();
        }
    }

    private String createAssistantPrompt(String productContext, String userPrompt) {

        String systemInstruction =
                "You are 'SkinMe Assistant,' a professional and friendly skincare advisor. " +
                        "Your primary goal is to provide helpful skincare advice and recommend products " +
                        "from the provided database context ONLY. " +
                        "DO NOT recommend any product not explicitly listed in the 'AVAILABLE PRODUCTS' section. " +
                        "Use the product name, brand, and type to form your recommendations.";

        String contextSection = "\n\n--- AVAILABLE PRODUCTS IN DATABASE ---\n" + productContext + "\n--- END OF PRODUCTS ---";

        String userQuestion = "\n\n--- CUSTOMER QUESTION ---\n" + userPrompt;

        return systemInstruction + contextSection + userQuestion;
    }

    private String formatProductsForGemini(List<Product> products) {
        if (products.isEmpty()) {
            return "NOTE: No specific product data was retrieved from the database. Answer based purely on general skincare knowledge but state that you cannot provide product recommendations at this time.";
        }

        StringBuilder sb = new StringBuilder();
        for (Product p : products) {
            sb.append("{\n");
            sb.append("  PRODUCT_ID: ").append(p.getId()).append("\n");
            sb.append("  NAME: ").append(p.getName()).append("\n");
            sb.append("  BRAND: ").append(p.getBrand() != null ? p.getBrand().getName() : "N/A").append("\n");
            sb.append("  TYPE: ").append(p.getProductType()).append("\n");
            sb.append("  Description: ").append(p.getDescription()).append("\n");

            String keyFeatures = p.getDescription() != null ?
                                 p.getDescription().replace("\n", " ") : "Key features not available.";
            sb.append("  KEY_FEATURES: ").append(keyFeatures).append("\n");
            sb.append("},\n");
        }
        return sb.toString();
    }


}
