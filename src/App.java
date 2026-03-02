import javax.swing.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.nio.charset.StandardCharsets;

class Conta {
    int id;
    String usuario;
    String senha;
    String nome;
    String conta;
    double saldo;
    // nova lista para armazenar as transações desta conta
    List<String> transacoes;

    public Conta(int id, String usuario, String senha, String nome, String conta, double saldo) {
        this.id = id;
        this.usuario = usuario;
        this.senha = senha;
        this.nome = nome;
        this.conta = conta;
        this.saldo = saldo;
        this.transacoes = new ArrayList<>();
    }
}

public class App {
    static List<Conta> contas = new ArrayList<>();
    static Conta contaAtiva = null;
    static JLabel saldoLabelReferencia = null;
    static final String CAMINHO_JSON = "contas.json";
    static AtomicBoolean ignorarProximaMudanca = new AtomicBoolean(false);
    // timestamp until which modifications are ignored (ms)
    static long ignorarAte = 0;    

    public static void main(String[] args) throws Exception {
        carregarContas();
        iniciarMonitoramentoArquivo();
        abrirTelaLogin();
    }

    static synchronized void carregarContas() {
        try {
            Path path = Paths.get(CAMINHO_JSON);
            if (!Files.exists(path))
                return;

            String conteudo = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            conteudo = conteudo.replaceAll("\\s+", " ");

            int inicioArray = conteudo.indexOf("\"contas\": [");
            if (inicioArray == -1)
                inicioArray = conteudo.indexOf("\"contas\":[");

            if (inicioArray == -1)
                return;

            int inicio = conteudo.indexOf("[", inicioArray);
            int fim = conteudo.lastIndexOf("]");
            String arrayContas = conteudo.substring(inicio + 1, fim);

            List<Conta> novasContas = new ArrayList<>();
            int contador = 0;
            while (true) {
                int posicaoId = arrayContas.indexOf("\"id\":", contador);
                if (posicaoId == -1)
                    break;

                int proximoId = arrayContas.indexOf("\"id\":", posicaoId + 5);
                int fimObjeto;
                if (proximoId == -1) {
                    fimObjeto = arrayContas.lastIndexOf("}");
                } else {
                    fimObjeto = arrayContas.lastIndexOf("}", proximoId);
                }

                String objeto = arrayContas.substring(arrayContas.lastIndexOf("{", posicaoId), fimObjeto + 1);

                try {
                    int id = extrairInt(objeto, "\"id\"");
                    String usuario = extrairString(objeto, "\"usuario\"");
                    String senha = extrairString(objeto, "\"senha\"");
                    String nome = extrairString(objeto, "\"nome\"");
                    String conta = extrairString(objeto, "\"conta\"");
                    double saldo = extrairDouble(objeto, "\"saldo\"");

                    Conta c = new Conta(id, usuario, senha, nome, conta, saldo);
                    // ao carregar, também extrai as transações se existirem
                    List<String> trans = extrairArrayDeStrings(objeto, "\"transacoes\"");
                    c.transacoes.addAll(trans);
                    novasContas.add(c);
                } catch (Exception e) {
                }
                contador = fimObjeto + 1;
            }

            contas = novasContas;

            // Atualiza conta ativa e UI
            if (contaAtiva != null) {
                for (Conta c : contas) {
                    if (c.id == contaAtiva.id) {
                        contaAtiva.saldo = c.saldo;
                        if (saldoLabelReferencia != null) {
                            SwingUtilities.invokeLater(() -> {
                                saldoLabelReferencia.setText("Saldo: R$ " + String.format("%.2f", contaAtiva.saldo));
                            });
                        }
                        break;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Erro ao carregar: " + e.getMessage());
        }
    }

    static synchronized void salvarContas() {
        try {
            ignorarProximaMudanca.set(true);
            // ignore events for next half second
            ignorarAte = System.currentTimeMillis() + 500;
            StringBuilder json = new StringBuilder("{\n  \"contas\": [\n");
            for (int i = 0; i < contas.size(); i++) {
                Conta c = contas.get(i);
                json.append("    {\n");
                json.append("      \"id\": ").append(c.id).append(",\n");
                json.append("      \"usuario\": \"").append(c.usuario).append("\",\n");
                json.append("      \"senha\": \"").append(c.senha).append("\",\n");
                json.append("      \"nome\": \"").append(c.nome).append("\",\n");
                json.append("      \"conta\": \"").append(c.conta).append("\",\n");
                json.append("      \"saldo\": ").append(String.format(Locale.US, "%.2f", c.saldo)).append(",\n");
                // salva transações também
                json.append("      \"transacoes\": [\n");
                for (int j = 0; j < c.transacoes.size(); j++) {
                    String t = c.transacoes.get(j).replace("\"", "\\\"");
                    json.append("        \"").append(t).append("\"").append(j < c.transacoes.size() - 1 ? "," : "").append("\n");
                }
                json.append("      ]\n");
                json.append("    }").append(i < contas.size() - 1 ? "," : "").append("\n");
            }
            json.append("  ]\n}");
            Files.write(Paths.get(CAMINHO_JSON), json.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, "Erro ao salvar: " + e.getMessage());
        }
    }

    static void iniciarMonitoramentoArquivo() {
        new Thread(() -> {
            try {
                WatchService watcher = FileSystems.getDefault().newWatchService();
                Paths.get(".").register(watcher, StandardWatchEventKinds.ENTRY_MODIFY);

                while (true) {
                    WatchKey key = watcher.take();
                    for (WatchEvent<?> event : key.pollEvents()) {
                        if (event.context().toString().equals(CAMINHO_JSON)) {
                            long now = System.currentTimeMillis();
                            if (now < ignorarAte) {
                                // ainda ignorando alterações recentes
                                continue;
                            }
                            if (ignorarProximaMudanca.get()) {
                                ignorarProximaMudanca.set(false);
                            } else {
                                Thread.sleep(100); // Espera o arquivo ser liberado
                                carregarContas();
                            }
                        }
                    }
                    if (!key.reset())
                        break;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    static int extrairInt(String texto, String chave) {
        int inicio = texto.indexOf(chave) + chave.length();
        int fim = texto.indexOf(",", inicio);
        if (fim == -1) {
            fim = texto.indexOf("}", inicio);
        }
        String valor = texto.substring(inicio, fim).trim().replaceAll("[^0-9]", "");
        return Integer.parseInt(valor);
    }

    static String extrairString(String texto, String chave) {
        int inicio = texto.indexOf(chave) + chave.length();
        // Pula até a primeira aspas
        inicio = texto.indexOf("\"", inicio) + 1;
        int fim = texto.indexOf("\"", inicio);
        return texto.substring(inicio, fim).trim();
    }

    static double extrairDouble(String texto, String chave) {
        int inicio = texto.indexOf(chave) + chave.length();
        int fim = texto.indexOf(",", inicio);
        if (fim == -1) {
            fim = texto.indexOf("}", inicio);
        }
        String valor = texto.substring(inicio, fim).trim().replaceAll("[^0-9.]", "");
        return Double.parseDouble(valor);
    }

    // extrai um array de strings ("transacoes") de um objeto JSON simplificado
    static List<String> extrairArrayDeStrings(String texto, String chave) {
        int pos = texto.indexOf(chave);
        List<String> lista = new ArrayList<>();
        if (pos == -1) {
            return lista;
        }
        int inicio = texto.indexOf("[", pos);
        int fim = texto.indexOf("]", inicio);
        if (inicio == -1 || fim == -1) {
            return lista;
        }
        String conteudo = texto.substring(inicio + 1, fim);
        int idx = 0;
        while (true) {
            int q1 = conteudo.indexOf("\"", idx);
            if (q1 == -1) break;
            int q2 = conteudo.indexOf("\"", q1 + 1);
            if (q2 == -1) break;
            String val = conteudo.substring(q1 + 1, q2);
            lista.add(val);
            idx = q2 + 1;
        }
        return lista;
    }

    static String timestamp() {
        return java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    static void abrirTelaLogin() {
        JFrame frame = new JFrame("Caixa Eletrônico - Login");
        frame.setSize(400, 300);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);
        frame.setResizable(false);

        JPanel panel = new JPanel();
        panel.setLayout(null);
        panel.setBackground(new java.awt.Color(240, 240, 240));

        JLabel titulo = new JLabel("Login");
        titulo.setBounds(160, 20, 80, 30);
        titulo.setFont(new java.awt.Font("Arial", java.awt.Font.BOLD, 24));
        panel.add(titulo);

        JLabel labelUsuario = new JLabel("Usuário:");
        labelUsuario.setBounds(50, 70, 80, 25);
        panel.add(labelUsuario);

        JTextField campoUsuario = new JTextField();
        campoUsuario.setBounds(130, 70, 220, 25);
        panel.add(campoUsuario);

        JLabel labelSenha = new JLabel("Senha:");
        labelSenha.setBounds(50, 110, 80, 25);
        panel.add(labelSenha);

        JPasswordField campoSenha = new JPasswordField();
        campoSenha.setBounds(130, 110, 220, 25);
        panel.add(campoSenha);

        JButton botaoLogin = new JButton("Entrar");
        botaoLogin.setBounds(130, 160, 100, 35);
        botaoLogin.setFont(new java.awt.Font("Arial", java.awt.Font.PLAIN, 14));
        panel.add(botaoLogin);

        JLabel resultado = new JLabel("");
        resultado.setBounds(50, 210, 300, 25);
        resultado.setFont(new java.awt.Font("Arial", java.awt.Font.PLAIN, 12));
        panel.add(resultado);

        botaoLogin.addActionListener(e -> {
            String usuario = campoUsuario.getText();
            String senha = new String(campoSenha.getPassword());

            if (usuario.isEmpty() || senha.isEmpty()) {
                resultado.setText("Preencha todos os campos!");
                resultado.setForeground(new java.awt.Color(255, 0, 0));
            } else {
                Conta contaBuscada = null;
                for (Conta c : contas) {
                    if (c.usuario.equals(usuario) && c.senha.equals(senha)) {
                        contaBuscada = c;
                        break;
                    }
                }

                if (contaBuscada != null) {
                    contaAtiva = contaBuscada;
                    frame.dispose();
                    abrirCaixaEletronico();
                } else {
                    resultado.setText("Usuário ou senha incorretos!");
                    resultado.setForeground(new java.awt.Color(255, 0, 0));
                    campoSenha.setText("");
                }
            }
        });

        frame.add(panel);
        frame.setVisible(true);
    }

    static void abrirCaixaEletronico() {
        JFrame frame = new JFrame("Caixa Eletrônico");
        frame.setSize(500, 600);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);
        frame.setResizable(false);

        JPanel panel = new JPanel();
        panel.setLayout(null);
        panel.setBackground(new java.awt.Color(30, 80, 150));

        // Tela de exibição
        JPanel telaSaldo = new JPanel();
        telaSaldo.setBounds(50, 30, 400, 100);
        telaSaldo.setBackground(new java.awt.Color(200, 200, 200));
        telaSaldo.setLayout(null);
        telaSaldo.setBorder(BorderFactory.createLineBorder(java.awt.Color.BLACK, 3));
        panel.add(telaSaldo);

        JLabel textoTela = new JLabel("BEM-VINDO, " + contaAtiva.nome.toUpperCase() + "!");
        textoTela.setBounds(50, 15, 300, 25);
        textoTela.setFont(new java.awt.Font("Arial", java.awt.Font.BOLD, 14));
        telaSaldo.add(textoTela);

        JLabel saldoTela = new JLabel("Saldo: R$ " + String.format("%.2f", contaAtiva.saldo));
        saldoTela.setBounds(80, 50, 240, 25);
        saldoTela.setFont(new java.awt.Font("Courier", java.awt.Font.PLAIN, 14));
        telaSaldo.add(saldoTela);
        saldoLabelReferencia = saldoTela;

        JLabel contaTela = new JLabel("Conta: " + contaAtiva.conta);
        contaTela.setBounds(100, 70, 240, 20);
        contaTela.setFont(new java.awt.Font("Courier", java.awt.Font.PLAIN, 12));
        telaSaldo.add(contaTela);

        // Botões do menu
        JButton botaoSaque = new JButton("SAQUE");
        botaoSaque.setBounds(50, 160, 180, 60);
        botaoSaque.setFont(new java.awt.Font("Arial", java.awt.Font.BOLD, 14));
        botaoSaque.setBackground(new java.awt.Color(0, 150, 0));
        botaoSaque.setForeground(java.awt.Color.WHITE);
        panel.add(botaoSaque);

        JButton botaoDeposito = new JButton("DEPÓSITO");
        botaoDeposito.setBounds(270, 160, 180, 60);
        botaoDeposito.setFont(new java.awt.Font("Arial", java.awt.Font.BOLD, 14));
        botaoDeposito.setBackground(new java.awt.Color(0, 150, 0));
        botaoDeposito.setForeground(java.awt.Color.WHITE);
        panel.add(botaoDeposito);

        JButton botaoTransferencia = new JButton("TRANSFERÊNCIA");
        botaoTransferencia.setBounds(50, 240, 180, 60);
        botaoTransferencia.setFont(new java.awt.Font("Arial", java.awt.Font.BOLD, 14));
        botaoTransferencia.setBackground(new java.awt.Color(0, 100, 200));
        botaoTransferencia.setForeground(java.awt.Color.WHITE);
        panel.add(botaoTransferencia);

        JButton botaoExtrato = new JButton("EXTRATO");
        botaoExtrato.setBounds(270, 240, 180, 60);
        botaoExtrato.setFont(new java.awt.Font("Arial", java.awt.Font.BOLD, 14));
        botaoExtrato.setBackground(new java.awt.Color(0, 100, 200));
        botaoExtrato.setForeground(java.awt.Color.WHITE);
        panel.add(botaoExtrato);

        JButton botaoSair = new JButton("SAIR");
        botaoSair.setBounds(160, 480, 180, 60);
        botaoSair.setFont(new java.awt.Font("Arial", java.awt.Font.BOLD, 14));
        botaoSair.setBackground(new java.awt.Color(200, 0, 0));
        botaoSair.setForeground(java.awt.Color.WHITE);
        panel.add(botaoSair);

        // Ações dos botões
        botaoSaque.addActionListener(e -> abrirTelamSaque(frame, saldoTela));
        botaoDeposito.addActionListener(e -> abrirTelaDeposito(frame, saldoTela));
        botaoTransferencia.addActionListener(e -> abrirTelaTransferencia(frame, saldoTela));
        botaoExtrato.addActionListener(e -> abrirTelaExtrato());
        botaoSair.addActionListener(e -> frame.dispose());

        frame.add(panel);
        frame.setVisible(true);
    }

    static void abrirTelamSaque(JFrame parent, JLabel saldoTela) {
        JFrame frame = new JFrame("Saque");
        frame.setSize(400, 250);
        frame.setLocationRelativeTo(parent);
        frame.setResizable(false);

        JPanel panel = new JPanel();
        panel.setLayout(null);
        panel.setBackground(new java.awt.Color(240, 240, 240));

        JLabel label = new JLabel("Valor do Saque (R$):");
        label.setBounds(50, 40, 150, 25);
        panel.add(label);

        JTextField campo = new JTextField();
        campo.setBounds(200, 40, 130, 25);
        panel.add(campo);

        JLabel resultado = new JLabel("");
        resultado.setBounds(50, 150, 280, 25);
        resultado.setFont(new java.awt.Font("Arial", java.awt.Font.PLAIN, 12));
        panel.add(resultado);

        JButton botaoConfirmar = new JButton("Confirmar");
        botaoConfirmar.setBounds(80, 100, 100, 35);
        panel.add(botaoConfirmar);

        JButton botaoCancelar = new JButton("Cancelar");
        botaoCancelar.setBounds(220, 100, 100, 35);
        panel.add(botaoCancelar);

        botaoConfirmar.addActionListener(e -> {
            try {
                double valor = Double.parseDouble(campo.getText());
                if (valor <= 0) {
                    resultado.setText("Valor deve ser maior que zero!");
                    resultado.setForeground(new java.awt.Color(255, 0, 0));
                } else if (valor > contaAtiva.saldo) {
                    resultado.setText("Saldo insuficiente!");
                    resultado.setForeground(new java.awt.Color(255, 0, 0));
                } else {
                    contaAtiva.saldo -= valor;
                    // registra transação
                    contaAtiva.transacoes.add(timestamp() + " - Saque de R$ " + String.format("%.2f", valor));
                    salvarContas();
                    resultado.setText("Saque de R$ " + String.format("%.2f", valor) + " realizado!");
                    resultado.setForeground(new java.awt.Color(0, 128, 0));
                    saldoTela.setText("Saldo: R$ " + String.format("%.2f", contaAtiva.saldo));
                    campo.setText("");

                }
            } catch (NumberFormatException ex) {
                resultado.setText("Digite um valor válido!");
                resultado.setForeground(new java.awt.Color(255, 0, 0));
            }
        });

        botaoCancelar.addActionListener(e -> frame.dispose());

        frame.add(panel);
        frame.setVisible(true);
    }

    static void abrirTelaDeposito(JFrame parent, JLabel saldoTela) {
        JFrame frame = new JFrame("Depósito");
        frame.setSize(400, 250);
        frame.setLocationRelativeTo(parent);
        frame.setResizable(false);

        JPanel panel = new JPanel();
        panel.setLayout(null);
        panel.setBackground(new java.awt.Color(240, 240, 240));

        JLabel label = new JLabel("Valor do Depósito (R$):");
        label.setBounds(50, 40, 150, 25);
        panel.add(label);

        JTextField campo = new JTextField();
        campo.setBounds(200, 40, 130, 25);
        panel.add(campo);

        JLabel resultado = new JLabel("");
        resultado.setBounds(50, 150, 280, 25);
        resultado.setFont(new java.awt.Font("Arial", java.awt.Font.PLAIN, 12));
        panel.add(resultado);

        JButton botaoConfirmar = new JButton("Confirmar");
        botaoConfirmar.setBounds(80, 100, 100, 35);
        panel.add(botaoConfirmar);

        JButton botaoCancelar = new JButton("Cancelar");
        botaoCancelar.setBounds(220, 100, 100, 35);
        panel.add(botaoCancelar);

        botaoConfirmar.addActionListener(e -> {
            try {
                double valor = Double.parseDouble(campo.getText());
                if (valor <= 0) {
                    resultado.setText("Valor deve ser maior que zero!");
                    resultado.setForeground(new java.awt.Color(255, 0, 0));
                } else {
                    contaAtiva.saldo += valor;
                    // registra transação
                    contaAtiva.transacoes.add(timestamp() + " - Depósito de R$ " + String.format("%.2f", valor));
                    salvarContas();
                    resultado.setText("Depósito de R$ " + String.format("%.2f", valor) + " realizado!");
                    resultado.setForeground(new java.awt.Color(0, 128, 0));
                    saldoTela.setText("Saldo: R$ " + String.format("%.2f", contaAtiva.saldo));
                    campo.setText("");

                }
            } catch (NumberFormatException ex) {
                resultado.setText("Digite um valor válido!");
                resultado.setForeground(new java.awt.Color(255, 0, 0));
            }
        });

        botaoCancelar.addActionListener(e -> frame.dispose());

        frame.add(panel);
        frame.setVisible(true);
    }

    static void abrirTelaTransferencia(JFrame parent, JLabel saldoTela) {
        JFrame frame = new JFrame("Transferência");
        frame.setSize(450, 320);
        frame.setLocationRelativeTo(parent);
        frame.setResizable(false);

        JPanel panel = new JPanel();
        panel.setLayout(null);
        panel.setBackground(new java.awt.Color(240, 240, 240));

        JLabel labelConta = new JLabel("Número da Conta Destino:");
        labelConta.setBounds(50, 40, 150, 25);
        panel.add(labelConta);

        JTextField campoConta = new JTextField();
        campoConta.setBounds(200, 40, 180, 25);
        panel.add(campoConta);

        JLabel labelValor = new JLabel("Valor (R$):");
        labelValor.setBounds(50, 80, 150, 25);
        panel.add(labelValor);

        JTextField campoValor = new JTextField();
        campoValor.setBounds(200, 80, 180, 25);
        panel.add(campoValor);

        JLabel resultado = new JLabel("");
        resultado.setBounds(50, 200, 350, 25);
        resultado.setFont(new java.awt.Font("Arial", java.awt.Font.PLAIN, 12));
        panel.add(resultado);

        JButton botaoConfirmar = new JButton("Confirmar");
        botaoConfirmar.setBounds(100, 130, 100, 35);
        panel.add(botaoConfirmar);

        JButton botaoCancelar = new JButton("Cancelar");
        botaoCancelar.setBounds(250, 130, 100, 35);
        panel.add(botaoCancelar);

        botaoConfirmar.addActionListener(e -> {
            try {
                String contaDestino = campoConta.getText().trim();
                double valor = Double.parseDouble(campoValor.getText().trim());

                if (contaDestino.isEmpty()) {
                    resultado.setText("Digite o número da conta!");
                    resultado.setForeground(new java.awt.Color(255, 0, 0));
                    return;
                }

                if (valor <= 0) {
                    resultado.setText("Valor deve ser maior que zero!");
                    resultado.setForeground(new java.awt.Color(255, 0, 0));
                    return;
                }

                if (valor > contaAtiva.saldo) {
                    resultado.setText("Saldo insuficiente!");
                    resultado.setForeground(new java.awt.Color(255, 0, 0));
                    return;
                }

                // Procura a conta de destino
                Conta contaBuscada = null;
                for (Conta c : contas) {
                    if (c.conta.equals(contaDestino)) {
                        contaBuscada = c;
                        break;
                    }
                }

                if (contaBuscada == null) {
                    resultado.setText("Conta de destino não encontrada!");
                    resultado.setForeground(new java.awt.Color(255, 0, 0));
                } else if (contaBuscada.conta.equals(contaAtiva.conta)) {
                    resultado.setText("Não é possível transferir para sua própria conta!");
                    resultado.setForeground(new java.awt.Color(255, 0, 0));
                } else {
                    // Realiza a transferência
                    contaAtiva.saldo -= valor;
                    contaBuscada.saldo += valor;
                    // registra transações em ambas as contas
                    contaAtiva.transacoes.add(timestamp() + " - Transferência de R$ " + String.format("%.2f", valor) + " para " + contaBuscada.conta);
                    contaBuscada.transacoes.add(timestamp() + " - Transferência recebida de " + contaAtiva.conta + " valor R$ " + String.format("%.2f", valor));
                    salvarContas();

                    // Atualiza o saldo na tela anterior
                    saldoTela.setText("Saldo: R$ " + String.format("%.2f", contaAtiva.saldo));

                    JOptionPane.showMessageDialog(frame,
                            "Transferência realizada com sucesso!\n\n" +
                                    "Valor: R$ " + String.format("%.2f", valor) + "\n" +
                                    "Para: " + contaBuscada.nome + "\n" +
                                    "Novo Saldo: R$ " + String.format("%.2f", contaAtiva.saldo),
                            "Sucesso",
                            JOptionPane.INFORMATION_MESSAGE);

                    campoConta.setText("");
                    campoValor.setText("");
                    resultado.setText("");
                }
            } catch (NumberFormatException ex) {
                resultado.setText("Digite um valor válido!");
                resultado.setForeground(new java.awt.Color(255, 0, 0));
            }
        });

        botaoCancelar.addActionListener(e -> frame.dispose());

        frame.add(panel);
        frame.setVisible(true);
    }

    static void abrirTelaExtrato() {
        JFrame frame = new JFrame("Extrato");
        frame.setSize(500, 450);
        frame.setLocationRelativeTo(null);
        frame.setResizable(false);

        JPanel panel = new JPanel();
        panel.setLayout(null);
        panel.setBackground(new java.awt.Color(240, 240, 240));

        JLabel titulo = new JLabel("EXTRATO BANCÁRIO");
        titulo.setBounds(150, 20, 200, 25);
        titulo.setFont(new java.awt.Font("Arial", java.awt.Font.BOLD, 14));
        panel.add(titulo);

        JLabel texto1 = new JLabel("Titular: " + contaAtiva.nome);
        texto1.setBounds(50, 60, 400, 20);
        panel.add(texto1);

        JLabel texto2 = new JLabel("Conta: " + contaAtiva.conta);
        texto2.setBounds(50, 90, 400, 20);
        panel.add(texto2);

        JLabel texto3 = new JLabel("Saldo Atual: R$ " + String.format("%.2f", contaAtiva.saldo));
        texto3.setBounds(50, 120, 400, 20);
        texto3.setFont(new java.awt.Font("Arial", java.awt.Font.BOLD, 12));
        panel.add(texto3);

        JLabel texto4 = new JLabel("Data: " + java.time.LocalDate.now());
        texto4.setBounds(50, 150, 400, 20);
        panel.add(texto4);

        // área de transações
        JTextArea area = new JTextArea();
        area.setEditable(false);
        for (String t : contaAtiva.transacoes) {
            area.append(t + "\n");
        }
        JScrollPane scroll = new JScrollPane(area);
        scroll.setBounds(50, 180, 380, 180);
        panel.add(scroll);

        JButton botaoOk = new JButton("OK");
        botaoOk.setBounds(200, 370, 100, 35);
        panel.add(botaoOk);

        botaoOk.addActionListener(e -> frame.dispose());

        frame.add(panel);
        frame.setVisible(true);
    }
}
