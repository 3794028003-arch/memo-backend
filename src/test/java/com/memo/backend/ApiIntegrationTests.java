package com.memo.backend;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.test.context.ActiveProfiles;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@ActiveProfiles("test")
class ApiIntegrationTests {
    @Autowired MockMvc mockMvc;

    @Test
    void registerSuccessAndDuplicateUsernameReturns409() throws Exception {
        String username = username();
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(registerJson(username)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.username").value(username));
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(registerJson(username)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("USERNAME_ALREADY_EXISTS"));
    }

    @Test
    void loginReturnsJwtAndWrongPasswordReturns401() throws Exception {
        String username = username();
        register(username);
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"secret123\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty());
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void memosRequireTokenAndSupportCompleteCrud() throws Exception {
        mockMvc.perform(get("/api/memos")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        String token = accessTokenFor(username());
        long id = createMemo(token, "Shopping", "work");
        mockMvc.perform(get("/api/memos").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].id").value(id));
        mockMvc.perform(patch("/api/memos/{id}/favorite", id).header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.isFavorite").value(true));
        mockMvc.perform(patch("/api/memos/{id}/pin", id).header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.isPinned").value(true));
        mockMvc.perform(get("/api/memos").param("keyword", "Shop").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1));
        mockMvc.perform(get("/api/memos").param("category", "work").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1));
        mockMvc.perform(get("/api/memos").param("favorite", "true").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1));
        mockMvc.perform(put("/api/memos/{id}", id).header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Updated\",\"content\":\"Updated text\",\"category\":\"personal\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.category").value("personal"));
        mockMvc.perform(delete("/api/memos/{id}", id).header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void userCannotAccessModifyOrDeleteAnotherUsersMemo() throws Exception {
        String userAToken = accessTokenFor(username());
        String userBToken = accessTokenFor(username());
        long memoId = createMemo(userBToken, "Private", "private");
        mockMvc.perform(get("/api/memos").header("Authorization", bearer(userAToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
        mockMvc.perform(put("/api/memos/{id}", memoId).header("Authorization", bearer(userAToken)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Hack\",\"content\":\"Hack\",\"category\":\"x\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/memos/{id}", memoId).header("Authorization", bearer(userAToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void archiveIsUserScopedAndCombinesWithFilters() throws Exception {
        String ownerToken = accessTokenFor(username());
        String otherToken = accessTokenFor(username());
        long id = createMemo(ownerToken, "Archived search", "work");
        mockMvc.perform(patch("/api/memos/{id}/archive", id).header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.isArchived").value(true));
        mockMvc.perform(get("/api/memos").param("archived", "false").header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
        mockMvc.perform(get("/api/memos").param("archived", "true").param("keyword", "search").param("category", "work").header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1));
        mockMvc.perform(patch("/api/memos/{id}/archive", id).header("Authorization", bearer(otherToken))).andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/memos/{id}/archive", id).header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.isArchived").value(false));
    }

    private void register(String username) throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(registerJson(username)))
                .andExpect(status().isCreated());
    }

    private String accessTokenFor(String username) throws Exception {
        register(username);
        String response = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"secret123\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.data.accessToken");
    }

    private long createMemo(String token, String title, String category) throws Exception {
        String response = mockMvc.perform(post("/api/memos").header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\",\"content\":\"text\",\"category\":\"" + category + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.data.id")).longValue();
    }

    private String registerJson(String username) { return "{\"username\":\"" + username + "\",\"email\":\"" + username + "@example.com\",\"password\":\"secret123\"}"; }
    private String username() { return "user" + UUID.randomUUID().toString().replace("-", "").substring(0, 12); }
    private String bearer(String token) { return "Bearer " + token; }
}
