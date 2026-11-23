# Desktop Launcher

Launcher Android com ambiente de trabalho, barra de tarefas, painel de aplicações, centro de
ações e configurações. **Implementado só em Java**, com as vistas construídas em código
(`View`, `LinearLayout`, `GridView`…). Não usa WebView, HTML, CSS nem JavaScript.

**Versão:** 2.0 (`versionName` 2.0, `versionCode` 8)

## Funcionalidades

- **Ambiente de trabalho:** atalhos afixados com os ícones reais. Tamanho ajustável (32–72 px).
  Modo «Organizar»: toca num ícone e depois noutro para trocar os lugares.
- **Barra de tarefas:** botão de aplicações, pesquisa, aplicações afixadas, até três apps em
  segundo plano, volume, notificações e relógio. Altura (56–84 px) e ícones (20–44 px) ajustáveis.
- **Pesquisa:** encontra aplicações e definições; sem resultados oferece pesquisa no Google.
  Ignora acentos.
- **Painel de aplicações:** pesquisa e grelha. Toque longo: renomear, mudar o ícone, afixar,
  ocultar, desinstalar ou ver a informação.
- **Configurações:** perfil (nome e avatar), aspeto (papéis de parede e galeria), barra e
  ícones, aplicações instaladas e definições do Android.
- **Centro de ações:** Wi-Fi, Bluetooth, localização, volume, brilho e notificações recentes.

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
    AppsPanel.java                    painel de aplicações
    SettingsPanel.java                configurações do launcher
    NotificationPanel.java            centro de ações
    Dialogs.java                      diálogos (renomear, ícone, avatar, nome, desinstalar)
    AppCatalog.java                   lista de apps e ícones do sistema
    SystemActions.java                Wi-Fi, Bluetooth, som, brilho, notificações…
    DesktopNotificationListener.java  guarda as notificações recentes
    Prefs.java                        preferências (SharedPreferences)
    Icons.java                        nomes e acesso aos ícones
    Ui.java                           cores, medidas e funções de construção
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
