package org.example.service;

import org.example.database.entity.Client;
import org.example.database.entity.Interaction;
import org.example.database.repository.ClientRepository;
import org.example.database.repository.InteractionRepository;
import org.example.service.gemini.GeminiChatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebhookProcessingServiceTest {

    private static final String PAGE_ID = "PAGE_ID";
    private static final String SENDER_ID = "SENDER_ID";
    private static final String MESSAGE_ID = "m_1";
    private static final String MESSAGE_TEXT = "Hello";

    @Mock
    private GeminiChatService chatService;

    @Mock
    private InteractionRepository interactionRepository;

    @Mock
    private InstagramMessageService instagramMessageService;

    @Mock
    private ClientRepository clientRepository;

    @InjectMocks
    private WebhookProcessingService webhookProcessingService;

    private Client client;

    @BeforeEach
    void setUp() {
        client = new Client();
        client.setClientName("Test Client");
        client.setInstagramPageId(PAGE_ID);
        client.setAccessToken("ACCESS_TOKEN");
        client.setAiSystemPrompt("Prompt");
    }

    @Test
    void processWebhookPayload_shouldExitWhenPageNotRegistered() {
        when(clientRepository.findByInstagramPageId(PAGE_ID)).thenReturn(Optional.empty());

        webhookProcessingService.processWebhookPayload(buildTextMessagePayload());

        verify(clientRepository).findByInstagramPageId(PAGE_ID);
        verifyNoInteractions(interactionRepository, chatService, instagramMessageService);
    }

    @Test
    void processWebhookPayload_shouldSkipWhenDuplicateMessage() {
        when(clientRepository.findByInstagramPageId(PAGE_ID)).thenReturn(Optional.of(client));
        when(interactionRepository.existsByMessageId(MESSAGE_ID)).thenReturn(true);

        webhookProcessingService.processWebhookPayload(buildTextMessagePayload());

        verify(interactionRepository).existsByMessageId(MESSAGE_ID);
        verify(interactionRepository, never()).save(any(Interaction.class));
        verify(chatService, never()).sendMessage(any(), any(), any());
        verify(instagramMessageService, never()).sendReply(any(), any(), any());
    }

    @Test
    void processWebhookPayload_shouldIgnoreNonTextMessages() {
        when(clientRepository.findByInstagramPageId(PAGE_ID)).thenReturn(Optional.of(client));

        webhookProcessingService.processWebhookPayload(buildNonTextMessagePayload());

        verify(clientRepository).findByInstagramPageId(PAGE_ID);
        verifyNoInteractions(interactionRepository, chatService, instagramMessageService);
    }

    @Test
    void processWebhookPayload_shouldHandleSuccessfulScenario() {
        when(clientRepository.findByInstagramPageId(PAGE_ID)).thenReturn(Optional.of(client));
        when(interactionRepository.existsByMessageId(MESSAGE_ID)).thenReturn(false);
        when(chatService.sendMessage(client, SENDER_ID, MESSAGE_TEXT)).thenReturn("AI Response");

        webhookProcessingService.processWebhookPayload(buildTextMessagePayload());

        ArgumentCaptor<Interaction> interactionCaptor = ArgumentCaptor.forClass(Interaction.class);
        verify(interactionRepository, times(2)).save(interactionCaptor.capture());

        Interaction userInteraction = interactionCaptor.getAllValues().get(0);
        Interaction aiInteraction = interactionCaptor.getAllValues().get(1);

        assertThat(userInteraction.getAuthor()).isEqualTo("USER");
        assertThat(userInteraction.getText()).isEqualTo(MESSAGE_TEXT);
        assertThat(userInteraction.getMessageId()).isEqualTo(MESSAGE_ID);
        assertThat(userInteraction.getClient()).isEqualTo(client);
        assertThat(aiInteraction.getAuthor()).isEqualTo("AI");
        assertThat(aiInteraction.getText()).isEqualTo("AI Response");
        assertThat(aiInteraction.getClient()).isEqualTo(client);

        verify(chatService).sendMessage(client, SENDER_ID, MESSAGE_TEXT);
        verify(instagramMessageService).sendReply(client.getAccessToken(), SENDER_ID, "AI Response");
    }

    private String buildTextMessagePayload() {
        return """
                {
                  \"object\": \"page\",
                  \"entry\": [
                    {
                      \"id\": \"%s\",
                      \"messaging\": [
                        {
                          \"sender\": { \"id\": \"%s\" },
                          \"message\": {
                            \"mid\": \"%s\",
                            \"text\": \"%s\"
                          }
                        }
                      ]
                    }
                  ]
                }
                """.formatted(PAGE_ID, SENDER_ID, MESSAGE_ID, MESSAGE_TEXT);
    }

    private String buildNonTextMessagePayload() {
        return """
                {
                  \"object\": \"page\",
                  \"entry\": [
                    {
                      \"id\": \"%s\",
                      \"messaging\": [
                        {
                          \"sender\": { \"id\": \"%s\" },
                          \"message\": {
                            \"mid\": \"%s\",
                            \"attachments\": [
                              { \"type\": \"image\", \"payload\": { \"url\": \"http://example.com/image.jpg\" } }
                            ]
                          }
                        }
                      ]
                    }
                  ]
                }
                """.formatted(PAGE_ID, SENDER_ID, MESSAGE_ID);
    }
}
