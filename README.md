# 🏦 Simple ATM Simulator

Um simulador de Caixa Eletrônico funcional desenvolvido para demonstrar lógica de programação, autenticação de usuários e manipulação de persistência de dados utilizando arquivos JSON.

O projeto simula o comportamento real de um banco de dados simples, onde todas as operações financeiras são refletidas e salvas localmente em tempo real.

---

## 🚀 Funcionalidades

- **Autenticação Segura:** Sistema de login validado contra base de dados JSON.
- **Operações Bancárias:** - **Consulta de Saldo:** Visualização instantânea do status financeiro.
  - **Depósitos e Saques:** Atualização dinâmica com validação de limites.
- **Histórico de Transações:** Registro detalhado de movimentações dentro do perfil de cada usuário.
- **Persistência de Dados (JSON):** Todas as alterações (saques, depósitos, novas senhas) são salvas diretamente no arquivo `contas.json`, garantindo que os dados não sejam perdidos ao fechar a aplicação.

## 🛠️ Tecnologias Utilizadas

- **Linguagem:** [Java]
- **Armazenamento:** JSON (Persistência de dados local)
- **Paradigma:** Orientação a Objetos / Funcional [Ajustar conforme seu código]

## 📂 Estrutura de Dados

O projeto utiliza uma estrutura organizada no arquivo `contas.json`, permitindo uma escalabilidade simples para novos usuários ou novos tipos de transações.

### Usuários para Teste:
| Usuário | Senha | Nome |
| :--- | :--- | :--- |
| `admin` | `1234` | João Silva |
| `maria` | `5678` | Maria Santos |
| `carlos` | `9012` | Carlos Oliveira |
| `ana` | `3456` | Ana Costa |
| `pedro` | `7890` | Pedro Ferreira |

*(A lista completa de usuários está disponível no arquivo de configuração do sistema).*

## ⚙️ Como Executar

1. Clone o repositório:
   ```bash
   git clone [https://github.com/FabSyntaxDev/simulador-de-caixa-eletronico-funcional.git](https://github.com/FabSyntaxDev/simulador-de-caixa-eletronico-funcional.git)
