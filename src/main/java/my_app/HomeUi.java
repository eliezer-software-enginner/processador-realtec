package my_app;

import javafx.stage.FileChooser;
import javafx.stage.Stage;
import megalodonte.ComputedState;
import megalodonte.ForEachState;
import megalodonte.ListState;
import megalodonte.State;
import megalodonte.base.Redirect;
import megalodonte.components.*;
import megalodonte.props.ButtonProps;
import megalodonte.props.ColumnProps;
import megalodonte.props.TextProps;
import megalodonte.styles.ColumnStyler;
import megalodonte.styles.TextStyler;
import my_app.utils.Utils;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class HomeUi {
    ListState<Registro> registros = ListState.of(List.of());
    ListState<ContaSaldo> contasState = ListState.of(List.of());
    ListState<Inconsistencia> inconsistenciasState = ListState.of(List.of());
    State<Totais> totaisState = State.of(new Totais(BigDecimal.ZERO, BigDecimal.ZERO, 0));

    ComputedState<String> totalCreditos = ComputedState.of(()->"Total Créditos: " + Utils.toBRLCurrency(totaisState.get().totalCreditos()), totaisState);
    ComputedState<String> totalDebitos = ComputedState.of(()->"Total Débitos: " + Utils.toBRLCurrency(totaisState.get().totalDebitos()), totaisState);
    ComputedState<String> quantidadeLancamentos = ComputedState.of(()->"Quantidade de Lançamentos: " + totaisState.get().quantidadeLancamentos(), totaisState);

    //data;conta;tipo;valor;descriçã
    record Registro(String data, String conta, Tipo tipo, BigDecimal valor, String descricao){}
    record Totais(BigDecimal totalCreditos, BigDecimal totalDebitos, int quantidadeLancamentos){}
    enum Tipo{D,C}

    public Component render(Stage stage) {
        return new Column(null,new ColumnStyler().bgColor("#fff"))
                .c_child(new MenuBar().menu("Quem somos",
                        new MenuItem("Realtec", ()-> Redirect.to("https://realtec.com.br/")),
                        new MenuItem("Eliezer Dev", ()-> Redirect.to("https://github.com/eliezer-software-enginner")))
                )
                .c_child(new SpacerVertical(10))
                .c_child(new Column(new ColumnProps().paddingAll(10))
                        .c_child(
                                new Button("Escolher arquivo .csv", new ButtonProps().onClick(()-> handleClickSelectFile(stage))
                                        .bgColor("#068D9D")
                                        .textColor("white"))
                        )
                        .c_child(new SpacerVertical(10))
                        .c_child(totais())
                        .c_child(new SpacerVertical(10))
                        .c_child(header(contasState))
                        .c_child(new SpacerVertical(10))
                        .c_child(inconsistencias(inconsistenciasState))
                        .c_child(new SpacerVertical(20))
                        .c_child(new SimpleTable<Registro>().fromData(registros)
                                        .header()
                                        .columns()
                                        .column("Data", registro -> registro.data)
                                        .column("Conta", registro -> registro.conta)
                                        .column("Tipo", registro -> registro.tipo)
                                        .column("Valor", registro -> registro.valor)
                                        .column("Descrição", registro -> registro.descricao)
                                        .build()
                        )
                );
    }

     Component totais() {
        return new Column()
                .c_child(new Text("Totais", new TextProps().fontSize(13).bold()))
                .c_child(new Text(totalCreditos,  new TextProps().fontSize(13)))
                .c_child(new Text(totalDebitos,  new TextProps().fontSize(13)))
                .c_child(new Text(quantidadeLancamentos,  new TextProps().fontSize(13)));
    }

    record Inconsistencia(int linha, String conta, BigDecimal saldoNegativo, String descricao){}
     Component inconsistencias(ListState<Inconsistencia> inconsistenciasState) {
        ForEachState<Inconsistencia, Component> inconsistenciaEach = ForEachState.of(inconsistenciasState, i -> new Row()
                .r_child(new Text(
                        "Linha %d: Conta %s ficou negativa: %s - %s".formatted(i.linha(), i.conta(),Utils.toBRLCurrency(i.saldoNegativo()), i.descricao()),
                        new TextProps().fontSize(12), new TextStyler().color("red"))));

        return new Column()
                .c_child(new Text("Inconsistências",  new TextProps().fontSize(13).bold()))
                .c_child(new Column().items(inconsistenciaEach));
    }

    record ContaSaldo(String conta, BigDecimal saldo){}
     Component header(ListState<ContaSaldo> contasState){
        ForEachState<ContaSaldo, Component> contasEach = ForEachState.of(contasState, c -> new Row()
                .r_child(new Text("Conta %s: %s".formatted(c.conta(), Utils.toBRLCurrency(c.saldo())))));

        return new Column()
                .c_child(new Text("Saldo final por conta", new TextProps().fontSize(13).bold()))
                .c_child(new Column().items(contasEach));
    }

    private  void handleClickSelectFile(Stage stage) {
        FileChooser fc = new FileChooser();
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("apenas arquivos .csv","*.csv"));
        var file = fc.showOpenDialog(stage);
        if(file != null){
            var contaValorMap = new HashMap<String, BigDecimal>();
            var inconsistencias = new ArrayList<Inconsistencia>();
            var totalCreditos = new AtomicReference<>(BigDecimal.ZERO);
            var totalDebitos = new AtomicReference<>(BigDecimal.ZERO);
            var quantidadeLancamentos = new AtomicInteger(0);
            var numeroLinha = new AtomicInteger(1);

            try(var lines = Files.lines( file.toPath())){
                lines.skip(1).forEach(linha-> {
                    numeroLinha.incrementAndGet();
                    var split = linha.split(";");
                    String data = split[0].trim();
                    String conta = split[1].trim();
                    var tipo = Tipo.valueOf(split[2].trim().toUpperCase());
                    var valor = new BigDecimal(split[3].trim());
                    String descricao = split[4].trim();

                    registros.add(new Registro(data, conta, tipo, valor, descricao));

                    if (tipo == Tipo.C) {
                        totalCreditos.updateAndGet(v -> v.add(valor));
                    } else {
                        totalDebitos.updateAndGet(v -> v.add(valor));
                    }
                    quantidadeLancamentos.incrementAndGet();

                    BigDecimal valorAtual = contaValorMap.getOrDefault(conta, BigDecimal.ZERO);
                    BigDecimal valorCalculado = tipo == Tipo.C
                            ? valorAtual.add(valor)
                            : valorAtual.subtract(valor);
                    contaValorMap.put(conta, valorCalculado);

                    if (valorCalculado.compareTo(BigDecimal.ZERO) < 0) {
                        inconsistencias.add(new Inconsistencia(numeroLinha.get(), conta, valorCalculado, descricao));
                    }
                });
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            var entries = contaValorMap.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(e -> new ContaSaldo(e.getKey(), e.getValue()))
                    .toList();
            contasState.set(entries);
            inconsistenciasState.set(inconsistencias);
            totaisState.set(new Totais(totalCreditos.get(), totalDebitos.get(), quantidadeLancamentos.get()));
        }
    };
}
