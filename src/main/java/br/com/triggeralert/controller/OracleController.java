package br.com.triggeralert.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import br.com.triggeralert.model.TriggerRequest;
import br.com.triggeralert.service.OracleService;

@RestController
@RequestMapping("/api/oracle")
public class OracleController {

    @Autowired
    private OracleService oracleService;

    @GetMapping("/tabelas/{tabela}/colunas")
    public List<String> listarColunas(@PathVariable String tabela) {
        return oracleService.listarColunas(tabela);
    }

    @PostMapping("/trigger")
    public ResponseEntity<?> criarTrigger(@RequestBody TriggerRequest req) {

        Object resultado = oracleService.criarTrigger(
            req.getTabela(),
            req.getNomeLog(),
            req.getCampos(),
            req.isExecutar()
        );

        // 🔹 Se for apenas gerar script (executar = false), resultado será String (SQL)
     
                String script = resultado.toString()
                .replace("\\n", "\n")
                .replace("\n", System.lineSeparator());
                
                return ResponseEntity.ok()
                .header("Content-Type", "text/plain; charset=UTF-8")
                .body(resultado.toString());
        

 
    }


    @DeleteMapping("/trigger/{triggerName}")
    public Map<String, Object> removerTrigger(@PathVariable String triggerName) {
        return oracleService.removerTrigger(triggerName);
    }

    @GetMapping("/logs/{nomeLog}")
    public Map<String, Object> consultarLogs(@PathVariable String nomeLog) {

        nomeLog = nomeLog.trim();
        List<Map<String, Object>> registros = oracleService.consultarLogs(nomeLog);

        Map<String, Object> resposta = new HashMap<>();
        resposta.put("log", nomeLog);
        resposta.put("quantidade", registros.size());
        resposta.put("registros", registros);

        return resposta;
    }
}
