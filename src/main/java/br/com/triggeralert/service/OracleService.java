package br.com.triggeralert.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class OracleService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // =========================
    // LISTAR COLUNAS DA TABELA
    // =========================
    public List<String> listarColunas(String tabela) {
        String sql =
            "SELECT column_name " +
            "FROM all_tab_columns " +
            "WHERE owner = 'DBAMV' " +
            "  AND table_name = ? " +
            "ORDER BY column_id";

        return jdbcTemplate.queryForList(sql, String.class, tabela.toUpperCase());
    }

    // =========================
    // CRIAR TRIGGER (OU SÓ SCRIPT)
    // =========================
    public Object criarTrigger(String tabela, String nomeLog, List<String> campos, boolean executar) {

        Map<String, Object> resposta = new HashMap<>();

        String triggerName = "TRG_CKBRA_" + tabela.toUpperCase();

        // Monta o SQL da trigger SEM executar ainda
        String sql = montarTriggerSQL(tabela, nomeLog, campos);

        // Se o usuário quer apenas o script, não executa no banco
        if (!executar) {
            resposta.put("script", sql);
            return resposta;
        }

        // Se for executar, primeiro verifica se já existe
        if (triggerExiste(triggerName)) {
            return Map.of("mensagem", "A trigger já existe no schema DBAMV");
        }

        // Tenta executar o CREATE TRIGGER
        try {
            jdbcTemplate.execute(sql);
        } catch (Exception e) {
            return Map.of("mensagem", "Erro Oracle ao executar o comando: " + e.getMessage());
        }

        // Verifica erros de compilação (ALL_ERRORS)
        List<String> erros = obterErrosTrigger(triggerName);

        if (!erros.isEmpty()) {
            resposta.put("mensagem", "Trigger criada com erros de compilação");
            resposta.put("trigger", triggerName);
            resposta.put("erros", erros);
            return resposta;
        }

        // Sucesso total
        resposta.put("status", "sucesso");
        resposta.put("mensagem", "Trigger criada e compilada sem erros");
        resposta.put("trigger", triggerName);
        return resposta;
    }

    // =========================
    // MONTAR SQL DA TRIGGER (SEMPRE AFTER)
    // =========================
    private String montarTriggerSQL(String tabela, String nomeLog, List<String> campos) {

        String triggerName = "TRG_CKBRA_" + tabela.toUpperCase();

        // Monta o corpo com os campos selecionados
        StringBuilder camposLog = new StringBuilder();
        StringBuilder camposUpdate = new StringBuilder();

        for (String campo : campos) {
            String campoUpper = campo.toUpperCase();

            // INSERT / DELETE -> só NEW 
            camposLog.append("'").append(campoUpper).append(": ' || :NEW.")
                     .append(campoUpper).append(" || '; ' ||");

            // UPDATE -> NEW e OLD
            camposUpdate.append("'").append(campoUpper).append(": ' || :NEW.")
                        .append(campoUpper).append(" || '; ' ||")
                        .append("'OLD.").append(campoUpper).append(": ' || :OLD.")
                        .append(campoUpper).append(" || '; ' ||");
        }

        String sql = ""
            + "CREATE OR REPLACE TRIGGER " + triggerName + " \n"
            + "AFTER INSERT OR UPDATE OR DELETE ON " + tabela.toUpperCase() + " \n"
            + "FOR EACH ROW \n"
            + "BEGIN \n"
            + "    IF INSERTING THEN \n"
            + "        dbamv.prc_grava_log_erro( \n"
            + "            '" + nomeLog + "', '" + nomeLog + "', '002', \n"
            + "            'LOG: ' || Dbms_Utility.FORMAT_CALL_STACK || \n"
            + "            'TELA:' || dbamv.pkg_mv2000.le_formulario || '; ' ||\n"
            + "            " + camposLog + "\n"
            + "             sys_context('USERENV', 'OS_USER') || ': ' ||\n"
            + "             sys_context('USERENV', 'HOST') || ': ' ||\n"
            + "             sys_context('USERENV', 'MODULE') || ': ' ||\n"
            + "             sys_context('USERENV', 'CLIENT_INFO'),\n"
            + "             10001                                    \n"
            + "        ); \n"
            + "    ELSIF UPDATING THEN \n"
            + "        dbamv.prc_grava_log_erro( \n"
            + "            '" + nomeLog + "', '" + nomeLog + "', '002', \n"
            + "            'LOG: ' || Dbms_Utility.FORMAT_CALL_STACK || \n"
            + "            'TELA:' || dbamv.pkg_mv2000.le_formulario || '; ' ||\n"
            + "             " + camposUpdate + "\n"
            + "             sys_context('USERENV', 'OS_USER') || ': ' ||\n"
            + "             sys_context('USERENV', 'HOST') || ': ' ||\n"
            + "             sys_context('USERENV', 'MODULE') || ': ' ||\n"
            + "             sys_context('USERENV', 'CLIENT_INFO'),\n"
            + "             10002                                    \n"
            + "        ); \n"
            + "    ELSE \n"
            + "        dbamv.prc_grava_log_erro( \n"
            + "            '" + nomeLog + "', '" + nomeLog + "', '002', \n"
            + "            'LOG: ' || Dbms_Utility.FORMAT_CALL_STACK || \n"
            + "            'TELA:' || dbamv.pkg_mv2000.le_formulario || '; ' ||\n"
            + "             " + camposLog + "\n"
            + "             sys_context('USERENV', 'OS_USER') || ': ' ||\n"
            + "             sys_context('USERENV', 'HOST') || ': ' ||\n"
            + "             sys_context('USERENV', 'MODULE') || ': ' ||\n"
            + "             sys_context('USERENV', 'CLIENT_INFO'),\n"
            + "             10003                                    \n"
            + "        ); \n"
            + "    END IF; \n"
            + "END;";

        return sql;
    }

    // =========================
    // VERIFICAR SE TRIGGER EXISTE (OWNER = DBAMV)
    // =========================
    public boolean triggerExiste(String triggerName) {
        String sql =
            "SELECT COUNT(*) FROM ALL_TRIGGERS " +
            "WHERE OWNER = 'DBAMV' AND TRIGGER_NAME = ?";

        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, triggerName.toUpperCase());
        return count != null && count > 0;
    }

    // =========================
    // OBTER ERROS DE COMPILAÇÃO DA TRIGGER
    // =========================
    public List<String> obterErrosTrigger(String triggerName) {
        String sql =
            "SELECT TEXT FROM ALL_ERRORS " +
            "WHERE OWNER = 'DBAMV' " +
            "  AND NAME = ? " +
            "  AND TYPE = 'TRIGGER' " +
            "ORDER BY SEQUENCE";

        return jdbcTemplate.queryForList(sql, String.class, triggerName.toUpperCase());
    }

    // =========================
    // DROPAR TRIGGER
    // =========================
    public Map<String, Object> removerTrigger(String triggerName) {
        Map<String, Object> resposta = new HashMap<>();

        String nomeUpper = triggerName.toUpperCase();

        if (!triggerExiste(nomeUpper)) {
            resposta.put("status", "nao_existe");
            resposta.put("mensagem", "Trigger não encontrada no schema DBAMV");
            resposta.put("trigger", nomeUpper);
            return resposta;
        }

        try {
            jdbcTemplate.execute("DROP TRIGGER " + nomeUpper);
            resposta.put("status", "sucesso");
            resposta.put("mensagem", "Trigger dropada com sucesso");
            resposta.put("trigger", nomeUpper);
        } catch (Exception e) {
            resposta.put("status", "erro_execucao");
            resposta.put("mensagem", "Erro ao dropar trigger");
            resposta.put("detalhe", e.getMessage());
            resposta.put("trigger", nomeUpper);
        }

        return resposta;
    }

    // =========================
    // CONSULTAR LOG_ERRO
    // =========================
    public List<Map<String, Object>> consultarLogs(String nomeLog) {

        String sql =
            "SELECT cd_erro, ds_erro " +
            "FROM log_erro " +
            "WHERE UPPER(forms) = UPPER(?) " +
            "ORDER BY id_log_erro DESC " +
            "FETCH FIRST 100 ROWS ONLY";

        return jdbcTemplate.queryForList(sql, nomeLog.trim());
    }


}
