# OrganizaUni

Aplicativo Android para organizar trabalhos, provas, exercícios e leituras por disciplina.

## As sete telas

1. Login com e-mail e senha e acesso à demonstração.
2. Cadastro com nome, e-mail, senha e confirmação.
3. Home com progresso, busca, filtros e lista de atividades.
4. Disciplinas com inclusão e exclusão mediante confirmação.
5. Formulário de atividade para criar ou editar título, disciplina, tipo, prazo e descrição.
6. Detalhes da atividade com conclusão, edição e exclusão mediante confirmação.
7. Perfil com edição de nome e curso e encerramento da sessão.

A contagem considera cadastro, login e Home como as três telas de base, acrescidas de quatro telas. Criar e editar uma atividade compartilham a mesma tela. A inclusão de disciplina usa um diálogo dentro da tela de disciplinas.

## Abrir e executar

Abra esta pasta no Android Studio compatível com Android Gradle Plugin 9.4.1. Aguarde a sincronização do Gradle. Use JDK 17 ou superior e instale o SDK Android API 37 e Build Tools 36.0.0 pelo SDK Manager. O projeto inclui o Gradle Wrapper 9.6.0.

Selecione um emulador ou celular com Android 8.0 (API 26) ou superior e execute o módulo `app`.

Na tela de login, use **Conhecer o app em demonstração** para explorar todas as telas internas com dados de exemplo. A demonstração mantém as alterações em memória durante a sessão. Ao sair dela ou encerrar o processo, os exemplos são reiniciados. As contas reais usam o Firebase.

Para compilar no Windows, execute `./gradlew.bat assembleDebug`. O APK será gerado em `app/build/outputs/apk/debug/app-debug.apk`. Execute `./gradlew.bat lintDebug` para análise estática.

## Conectar seu Firebase

1. Crie um projeto no console do Firebase e adicione um app Android com o pacote **br.com.organizauni**.
2. Baixe o arquivo `google-services.json` e coloque-o em `app/google-services.json`.
3. Em Authentication, habilite o provedor **E-mail/senha**.
4. Crie o banco **Cloud Firestore** padrão. Escolha a localização apropriada no console.
5. Na aba Regras do Firestore, publique o conteúdo de `firestore.rules` deste projeto.
6. Sincronize o Gradle e execute o app novamente. Cadastre uma conta pela tela Cadastro.

O plugin Google Services é aplicado quando o arquivo de configuração existe. Sem ele, o app abre e permite a demonstração, e o login informa como conectar o serviço.

O Firebase Authentication gerencia as credenciais. O Firestore guarda o perfil e os dados nas coleções `users/{uid}/subjects` e `users/{uid}/tasks`. As regras restringem o acesso à própria conta. O campo `dueDay` representa a data civil por meio de `LocalDate.toEpochDay()`, evitando alterações de prazo por fuso horário. A senha permanece no fluxo de autenticação.

Se o cadastro criar a conta e a gravação do perfil falhar, entre com as mesmas credenciais e salve o nome e o curso na tela Perfil. O usuário autenticado pode tentar novamente o carregamento pela mensagem de erro.

## Organização do código

- `data/StudyRepository.kt` contém os modelos, o contrato do repositório e as implementações Firebase e demonstração.
- `ui/StudyViewModel.kt` concentra o estado, validações e operações da aplicação.
- `ui/OrganizaUniApp.kt` contém as sete telas em Compose e a navegação.
- `ui/Theme.kt` define cores, tipografia e formas.
- `MainActivity.kt` inicializa a interface.

O projeto usa Kotlin, Jetpack Compose, Material 3, Navigation Compose, ViewModel, StateFlow, Firebase Authentication e Cloud Firestore. A interface acompanha alterações do banco por listeners removidos ao encerrar a sessão.

## Conferência dos fluxos

Na demonstração, abra Disciplinas e adicione uma matéria. Na Home, crie uma atividade para essa disciplina. Abra os detalhes, edite o prazo e marque a atividade como concluída. Verifique os filtros e a busca. Exclua a atividade, remova a disciplina e encerre a sessão pelo Perfil. No Firebase configurado, repita com duas contas para conferir que cada uma acessa seus próprios dados e que o login é mantido ao reabrir o aplicativo.

## Referências técnicas

- [Android Gradle Plugin 9.4](https://developer.android.com/build/releases/agp-9-4-0-release-notes)
- [Kotlin integrado ao Android Gradle Plugin](https://developer.android.com/build/migrate-to-built-in-kotlin)
- [Configuração do Firebase Android](https://firebase.google.com/docs/android/setup)

O escopo foi elaborado a partir das aulas fornecidas e da orientação de cadastro, login e Home mais quatro telas. A escolha de Compose acompanha a aula 10. A estrutura usa MVVM conforme a aula de arquitetura.
