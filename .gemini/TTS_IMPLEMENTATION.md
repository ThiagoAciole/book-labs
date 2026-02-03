# 🔊 Funcionalidade de Leitura de Voz (Text-to-Speech) para EPUB e PDF

## 📋 Visão Geral

Foi implementada uma funcionalidade completa de **Text-to-Speech (TTS)** para livros EPUB e PDF no aplicativo BookLabs. Esta funcionalidade permite que os usuários ouçam o conteúdo dos livros enquanto leem, oferecendo uma experiência de leitura mais acessível e versátil.

## ✨ Recursos Implementados

### 1. **Gerenciador de TTS** (`TextToSpeechManager.kt`)
- ✅ Inicialização automática com idioma PT-BR
- ✅ Controle de reprodução (Play/Pause/Stop)
- ✅ Navegação entre capítulos (Anterior/Próximo)
- ✅ Ajuste de velocidade de leitura (0.5x a 2.0x)
- ✅ Ajuste de pitch da voz (0.5x a 2.0x)
- ✅ Divisão inteligente de texto em sentenças
- ✅ Sincronização com a navegação do livro
- ✅ Estados observáveis com Flow/StateFlow

### 2. **Interface de Usuário**

#### **Barra Inferior Expandida**
- 🎮 **Controles de Reprodução:**
  - Botão Play/Pause principal
  - Botão Stop para parar completamente
  - Botões de navegação (Capítulo Anterior/Próximo)
  
- ⚡ **Controle de Velocidade:**
  - Slider para ajustar velocidade (0.5x a 2.0x)
  - Indicador visual da velocidade atual
  - 5 níveis de ajuste

- 🎨 **Design Premium:**
  - Painel expansível com animações suaves
  - Ícones intuitivos e coloridos
  - Feedback visual do estado de reprodução
  - Integração com o tema escuro/claro

#### **Botão de Acesso Rápido**
- Ícone de Play/Pause na barra inferior
- Destaque em roxo quando ativo
- Toggle para mostrar/ocultar controles completos

## 🎯 Como Usar

### **Passo 1: Abrir um Livro EPUB ou PDF**
1. Navegue até a biblioteca
2. Selecione um livro EPUB ou PDF
3. O livro será aberto no leitor

### **Passo 2: Ativar a Leitura de Voz**
1. Toque na tela para mostrar os controles
2. Na barra inferior, toque no ícone de **Play** (primeiro botão à esquerda)
3. O painel de controles de TTS será expandido

### **Passo 3: Controlar a Reprodução**
- **Play/Pause:** Toque no botão central grande para iniciar ou pausar
- **Stop:** Toque no botão de stop para parar e voltar ao início do capítulo
- **Navegar:** Use os botões de skip para ir ao capítulo anterior ou próximo
- **Velocidade:** Ajuste o slider para mudar a velocidade de leitura

### **Passo 4: Ajustar Configurações**
- Arraste o slider de velocidade para a esquerda (mais lento) ou direita (mais rápido)
- A velocidade atual é exibida ao lado do slider (ex: "1.5x")

## 🔧 Detalhes Técnicos

### **Arquitetura**

```
TextToSpeechManager
├── Estados (StateFlow)
│   ├── isPlaying: Boolean
│   ├── currentChapterIndex: Int
│   ├── speechRate: Float
│   └── pitch: Float
│
├── Controles
│   ├── play()
│   ├── pause()
│   ├── stop()
│   ├── goToChapter(index)
│   ├── playNextChapter()
│   ├── playPreviousChapter()
│   ├── setSpeechRate(rate)
│   └── setPitch(pitch)
│
└── Callbacks
    ├── onChapterChange
    └── onSentenceChange
```

### **Fluxo de Funcionamento**

1. **Inicialização:**
   - TTS é inicializado quando o `ReaderScreen` é criado
   - Idioma é configurado para PT-BR
   - Capítulos são carregados do livro

2. **Preparação:**
   - Cada capítulo é convertido de HTML para texto puro
   - Texto é dividido em sentenças usando regex
   - Sentenças são armazenadas para reprodução sequencial

3. **Reprodução:**
   - Sentenças são faladas uma por vez
   - Ao terminar uma sentença, avança automaticamente
   - Ao terminar um capítulo, avança para o próximo
   - Sincronização com a página atual do leitor

4. **Limpeza:**
   - TTS é desligado quando o leitor é fechado
   - Recursos são liberados adequadamente

### **Sincronização com Navegação**

- Quando o usuário muda de página manualmente, o TTS continua no capítulo atual
- Ao iniciar a reprodução, o TTS sincroniza com a página atual visível
- Mudanças de capítulo via TTS atualizam a página exibida

## 🎨 Personalização

### **Velocidade de Leitura**
- **0.5x:** Muito lento (ideal para aprendizado)
- **1.0x:** Normal (velocidade padrão)
- **1.5x:** Rápido (leitura dinâmica)
- **2.0x:** Muito rápido (revisão rápida)

### **Temas**
- A interface de TTS se adapta automaticamente ao tema escuro/claro
- Cores e ícones mudam conforme o tema selecionado

## 🚀 Melhorias Futuras (Sugestões)

1. **Destaque de Texto:**
   - Destacar a sentença sendo lida
   - Scroll automático para acompanhar a leitura

2. **Vozes Personalizadas:**
   - Seleção de diferentes vozes
   - Download de vozes adicionais

3. **Marcadores de Áudio:**
   - Salvar posição de áudio
   - Retomar do ponto exato

4. **Controles Avançados:**
   - Ajuste de pitch (tom da voz)
   - Pausas entre sentenças
   - Pronúncia personalizada

5. **Acessibilidade:**
   - Atalhos de teclado
   - Comandos de voz
   - Integração com fones Bluetooth

## 📝 Notas Importantes

- ✅ Funciona apenas com livros EPUB e PDF (baseados em texto)
- ✅ Requer permissão de TTS no dispositivo
- ✅ Idioma padrão: Português do Brasil (PT-BR)
- ✅ Fallback para idioma do sistema se PT-BR não disponível
- ✅ Gerenciamento automático de recursos
- ✅ Estados persistentes durante a leitura

## 🐛 Troubleshooting

### **TTS não funciona:**
1. Verifique se o dispositivo tem TTS instalado
2. Vá em Configurações > Idioma > Text-to-Speech
3. Instale um mecanismo de TTS (ex: Google TTS)

### **Voz em idioma errado:**
1. Baixe o pacote de idioma PT-BR
2. Configure PT-BR como idioma preferencial no TTS

### **Reprodução travada:**
1. Pare a reprodução
2. Feche e reabra o livro
3. Tente novamente

## 📄 Arquivos Modificados/Criados

1. **Criados:**
   - `TextToSpeechManager.kt` - Gerenciador principal de TTS

2. **Modificados:**
   - `ReaderScreen.kt` - Integração do TTS com o leitor
   - `ReaderNavigation.kt` - Adição de controles de TTS na UI

## 🎉 Conclusão

A funcionalidade de Text-to-Speech está totalmente implementada e pronta para uso! Os usuários agora podem desfrutar de uma experiência de leitura mais rica e acessível, podendo ouvir seus livros favoritos enquanto realizam outras atividades.

**Aproveite a leitura de voz! 📚🔊**
