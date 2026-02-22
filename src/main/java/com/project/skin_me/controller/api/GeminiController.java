package com.project.skin_me.controller.api;

import com.project.skin_me.service.chatAI.GeminiService;
import com.project.skin_me.util.MarkdownCatalogLoader;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("${api.prefix}/chat")
public class GeminiController {

    private final GeminiService geminiService;
    private final MarkdownCatalogLoader markdownCatalogLoader;

    @PostMapping("/assistant")
    public String askGeminiWithProducts(@RequestBody String userQuestion) {
        try {
            // 1. Load the latest markdown from the injected loader
            String markdownTable = markdownCatalogLoader.load();

            // 2. Build prompt
            String prompt = """
                   You are a helpful skincare assistant for SkinMe.
                Use ONLY the product data below (Markdown table) to answer.
                Do not invent products.
                
                When recommending a product, respond in HTML format with:
                - Product name
                - Product image (as <img src="..."/>)
                - Link to the product page (as <a href="...">Link</a>)
                
                PRODUCT CATALOG:
                %s
                
                USER QUESTION: %s
                
                If no match, say: "I couldn't find a matching product."
                """.formatted(markdownTable, userQuestion);

            return geminiService.askGemini(prompt);

        } catch (Exception e) {
            return "Sorry, the product catalog is temporarily unavailable. Please try again later.";
        }
    }
}
