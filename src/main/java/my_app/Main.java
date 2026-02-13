package my_app;
import megalodonte.application.MegalodonteApp;

public class Main {
    static void main() {
        MegalodonteApp.run(context-> {
            final var stage = context.javafxStage();
            stage.setTitle("Processador de movimentações financeiras por Eliezer");

            context.useView(new HomeUi().render(stage));
        });
    }
}
