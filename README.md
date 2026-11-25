# Desktop Launcher

Launcher Android com ambiente de trabalho, barra de tarefas, painel de aplicações, centro de
ações e configurações. **Implementado só em Java**, com as vistas construídas em código
(`View`, `LinearLayout`, `GridView`…). Não usa WebView, HTML, CSS nem JavaScript.

**Versão:** 2.6 (`versionName` 2.6; `versionCode` = 1000 + número da execução do CI)

## Funcionalidades

- **Ambiente de trabalho:** atalhos afixados com os ícones reais. Tamanho ajustável (32–72 px).
  Modo «Organizar»: toca num ícone e depois noutro para trocar os lugares.
- **Barra de tarefas:** botão de aplicações, pesquisa, aplicações afixadas, até três apps em
  segundo plano, volume, notificações e relógio. Altura (56–84 px) e ícones (20–44 px) ajustáveis.
- **Pesquisa:** encontra aplicações e definições; sem resultados oferece pesquisa no Google.
  Ignora acentos.
- **Painel de aplicações:** pesquisa e grelha. Toque longo ou clique direito: renomear, mudar o
  ícone, afixar, ocultar, desinstalar ou ver a informação.
- **Menu de contexto:** abre junto ao ícone tocado ou ao cursor, com ícones e atalhos visíveis.
  Funciona com toque, rato (clique direito) e teclado (setas, Enter, Esc).
- **Atalhos de teclado e rato:** ver a secção seguinte. Prime F1 ou Ctrl+/ para os ver no launcher.
- **Configurações:** perfil (nome e avatar), aspeto (papéis de parede e galeria), barra e
  ícones, aplicações instaladas e definições do Android.
- **Centro de ações:** Wi-Fi, Bluetooth, localização, volume, brilho e notificações recentes.

## Atalhos de teclado e rato

| Tecla | Ação |
|---|---|
| Win ou Ctrl+Esc | Abrir ou fechar as Aplicações |
| Ctrl+F ou / | Pesquisar na barra de tarefas |
| Win+A ou Ctrl+Shift+N | Abrir ou fechar o centro de ações |
| Win+D ou Ctrl+D | Mostrar o ambiente de trabalho |
| Ctrl+, | Configurações do launcher |
| Ctrl+Shift+O | Ligar ou desligar o modo Organizar |
| Setas ou Tab | Mover o foco entre os ícones |
| Enter | Abrir o ícone em foco |
| Shift+F10 ou Menu | Abrir o menu do ícone em foco |
| F2 | Renomear o ícone em foco |
| Esc | Fechar o que estiver aberto |
| F1 ou Ctrl+/ | Mostrar a lista de atalhos |

Com o rato: clique direito num ícone abre o seu menu; clique direito no ambiente abre o menu do
ambiente; passar o cursor destaca o ícone ou a opção. Quando um campo de texto tem o foco, as teclas
escrevem nele e os atalhos de letra não atuam.

Os atalhos estão em `Shortcuts.java`. A lista mostrada ao utilizador (`Shortcuts.HELP`) deve ser
atualizada sempre que um atalho mudar.

## Instalar por cima de versões anteriores

- Todas as builds são assinadas com a chave fixa `app/launcher-debug.keystore`. Por isso, uma
  build nova instala-se por cima da anterior sem conflito de assinatura.
- No CI, o `versionCode` é `1000 + GITHUB_RUN_NUMBER`, sempre crescente. Cada APK novo é uma
  versão mais recente e pode substituir o anterior.
- O Android não deixa instalar uma versão mais antiga por cima de uma mais nova. Para isso é
  preciso desinstalar primeiro (apaga os dados do launcher) ou usar `adb install -r -d`.
- A primeira instalação desta versão, por cima de uma build antiga com outra chave, precisa de
  uma desinstalação única. Depois disso, as atualizações funcionam normalmente.

## Requisitos

- Android 6.0 (API 23) ou superior.
- JDK 17, Android Gradle Plugin 7.4.2, Gradle 7.6.3, `compileSdk` e `targetSdk` 33.

## Compilar

O workflow `.github/workflows/android.yml` compila o APK de debug em cada push para `main` ou
`master`, e também pode ser iniciado manualmente em Actions. O APK fica no artefacto
`DesktopLauncher-debug`.

## Estrutura

```
app/src/main/
  java/com/tayson/desktop/
    MainActivity.java                 ciclo de vida e eventos do sistema
    Launcher.java                     ambiente de trabalho, barra, menus e ações
    ContextMenu.java                  menu de contexto (ícones, atalhos, teclado)
    Shortcuts.java                    atalhos de teclado e lista de ajuda
    AppsPanel.java                    painel de aplicações
    SettingsPanel.java                configurações do launcher
    NotificationPanel.java            centro de ações
    Dialogs.java                      diálogos (renomear, ícone, avatar, nome, desinstalar, atalhos)
    AppCatalog.java                   lista de apps e ícones do sistema
    SystemActions.java                Wi-Fi, Bluetooth, som, brilho, notificações…
    DesktopNotificationListener.java  guarda as notificações recentes
    Prefs.java                        preferências (SharedPreferences)
    Icons.java                        nomes e acesso aos ícones
    Ui.java                           paleta, estados de hover e foco, cursores e diálogos
  res/drawable/                       ícones vetoriais (ic_*.xml)
  res/drawable-nodpi/                 ícones de marca, avatares e papéis de parede (PNG/JPG)
  res/values/styles.xml
```

## Limitações conhecidas

- «Fechar app» encerra apenas os processos em segundo plano. Uma app visível pode continuar aberta.
- Em Android 10 ou superior, o launcher não liga nem desliga o Wi-Fi: abre o painel do sistema.
- Em Android 13 ou superior, o Bluetooth também só se controla pelas definições.
- A localização abre sempre as definições do sistema.
- O brilho pode exigir a autorização «Modificar definições do sistema».
- `targetSdk` está em 33 porque o AGP 7.4.2 não suporta versões superiores.
- Esta versão em Java puro ainda não foi validada num conjunto alargado de dispositivos.
- O cursor é definido por `PointerIcon` (Android 7 ou superior). Em versões anteriores o cursor
  continua a ser o do sistema.
- Os atalhos Win dependem de o teclado enviar a tecla Meta. Alguns teclados e computadores
  (ex.: Chromebooks e ambientes de desktop do Android) podem reservá-la para o sistema.
