package br.com.triggeralert.model;

import java.util.List;

public class TriggerRequest {

    private String tabela;
    private String nomeLog;
    private List<String> campos;
    private boolean executar;

    private String montarTriggerSQL(String tabela, String nomeLog, List<String> campos) {

    String triggerName = "TRG_" + tabela.toUpperCase();

    // Monta o corpo com os campos selecionados
    StringBuilder camposLog = new StringBuilder();

    for (String campo : campos) {
        camposLog.append("' ").append(campo).append(" NOVO:' || :NEW.")
                 .append(campo).append(" || ' ")
                 .append(campo).append(" ANTIGO:' || :OLD.")
                 .append(campo).append(" || ' '; ");
    }

    String sql = ""
        + "CREATE OR REPLACE TRIGGER " + triggerName + " \n"
        + "AFTER INSERT OR UPDATE OR DELETE ON " + tabela + " \n"
        + "FOR EACH ROW \n"
        + "BEGIN \n"
        + "    IF INSERTING THEN \n"
        + "        dbamv.prc_grava_log_erro( \n"
        + "            '" + nomeLog + "', '" + nomeLog + "', '002', \n"
        + "            'LOG: ' || Dbms_Utility.FORMAT_CALL_STACK || " + camposLog + " , \n"
        + "            10000 \n"
        + "        ); \n"
        + "    ELSIF UPDATING THEN \n"
        + "        dbamv.prc_grava_log_erro( \n"
        + "            '" + nomeLog + "', '" + nomeLog + "', '002', \n"
        + "            'LOG: ' || Dbms_Utility.FORMAT_CALL_STACK || " + camposLog + ", \n"
        + "            10002 \n"
        + "        ); \n"
        + "    ELSE \n"
        + "        dbamv.prc_grava_log_erro( \n"
        + "            '" + nomeLog + "', '" + nomeLog + "', '002', \n"
        + "            'LOG: ' || Dbms_Utility.FORMAT_CALL_STACK || " + camposLog + ", \n"
        + "            10003 \n"
        + "        ); \n"
        + "    END IF; \n"
        + "END;";
    
    return sql;
}


    public String getTabela() {
        return tabela;
    }

    public void setTabela(String tabela) {
        this.tabela = tabela;
    }

    public String getNomeLog() {
        return nomeLog;
    }

    public void setNomeLog(String nomeLog) {
        this.nomeLog = nomeLog;
    }

    public List<String> getCampos() {
        return campos;
    }

    public void setCampos(List<String> campos) {
        this.campos = campos;
    }
    public boolean isExecutar() {
    return executar;
    }      

    
}
