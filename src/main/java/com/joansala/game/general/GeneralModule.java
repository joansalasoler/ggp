package com.joansala.game.general;

/*
 * Samurai engine.
 * Copyright (C) 2021-2024 Joan Sala Soler <contact@joansala.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import com.google.inject.Provides;

import org.ggp.base.util.game.CloudGameRepository;
import org.ggp.base.util.game.GameRepository;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.implementation.prover.ProverStateMachine;
import org.ggp.base.util.statemachine.CachedStateMachine;

import com.joansala.cli.*;
import com.joansala.engine.*;
import com.joansala.engine.base.BaseModule;
import com.joansala.engine.partner.Partner;
import com.joansala.engine.mcts.Montecarlo;


/**
 * Binds together the components of the General engine.
 */
public class GeneralModule extends BaseModule {

    /** Game state machine */
    private static StateMachine machine;


    /**
     * Command line interface.
     */
    @Command(
      name = "ggp",
      version = "1.0.0",
      description =
        "General Game Playing games. A cloud repository and a game name " +
        "must be specified to launch the engine, for example, to play " +
        "Reversi with «games.ggp.org/stanford:reversi». Known repoistories " +
        "are «games.ggp.org/stanford» and «games.ggp.org/dresden».",
      subcommands = { ListCommand.class }
    )
    private static class GeneralCommand extends MainCommand {
        @Parameters(
            paramLabel = "<repository>[:<game>]",
            description = "Repository or cloud game identifier"
        )
        private static String gamePath;
    }

    @Command(
      name = "list",
      description = "List games on a remote repository",
      mixinStandardHelpOptions = true
    )
    static class ListCommand implements Runnable {
        @Override public void run() {
            printGames(GeneralCommand.gamePath);
        }
    }


    /**
     * Game module configuration.
     */
    @Override protected void configure() {
        bind(Game.class).to(GeneralGame.class);
        bind(Board.class).to(GeneralBoard.class);
    }


    /**
     * Recipe to start a new service.
     */
    @Override
    public String[] getServiceParameters() {
        String gamePath = GeneralCommand.gamePath;
        return new String[] { gamePath, "service" };
    }


    /**
     * State machine for the requested game.
     */
    @Provides
    public static StateMachine provideStateMachine() {
        return machine;
    }


    /**
     * Engine for the requested game.
     */
    @Provides
    public static Engine provideEngine(StateMachine machine) {
        return (machine.getRoles().size() == 1) ?
            new Partner() : new Montecarlo();
    }


    /**
     * Creates a new state machine for the given cloud game.
     */
    private static StateMachine createStateMachine(String path) {
        String[] uri = path.split(":");
        GameRepository repo = new CloudGameRepository(uri[0]);

        StateMachine sm = new ProverStateMachine();
        StateMachine machine = new CachedStateMachine(sm);
        machine.initialize(repo.getGame(uri[1]).getRules());

        return machine;
    }


    /**
     * List all the games on a remote repository.
     *
     * @param path      Cloud repository URI
     */
    private static void printGames(String path) {
        String[] uri = path.split(":");
        GameRepository repo = new CloudGameRepository(uri[0]);

        for (var key : repo.getGameKeys()) {
            var game = repo.getGame(key);
            String name = game.getName();
            System.out.format("%s:%s (%s)%n", uri[0], key, name);
        }
    }


    /**
     * Exectues the command line interface.
     *
     * @param args      Command line parameters
     */
    public static void main(String[] args) throws Exception {
        GeneralCommand main = new GeneralCommand();

        try {
            if (args.length > 0 && args[0].matches("[^:]+[:][^:]+")) {
                GeneralModule.machine = createStateMachine(args[0]);
            }
        } catch (Exception e) {
            BaseModule module = new GeneralModule();
            main.error(module, e);
            System.exit(1);
        } finally {
            BaseModule module = new GeneralModule();
            System.exit(main.execute(module, args));
        }
    }
}
