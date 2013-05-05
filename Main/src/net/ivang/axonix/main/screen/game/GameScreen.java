/*
 * Copyright 2012-2013 Ivan Gadzhega
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package net.ivang.axonix.main.screen.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.Align;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.esotericsoftware.tablelayout.Cell;
import net.ivang.axonix.main.AxonixGame;
import net.ivang.axonix.main.screen.BaseScreen;
import net.ivang.axonix.main.screen.game.actor.Level;
import net.ivang.axonix.main.screen.game.actor.background.Background;
import net.ivang.axonix.main.screen.game.actor.bar.DebugBar;
import net.ivang.axonix.main.screen.game.actor.bar.StatusBar;
import net.ivang.axonix.main.screen.game.actor.dialog.AlertDialog;
import net.ivang.axonix.main.screen.game.actor.notification.NotificationLabel;
import net.ivang.axonix.main.screen.game.input.GameScreenInputProcessor;

import static java.lang.Math.min;

/**
 * @author Ivan Gadzhega
 * @since 0.1
 */
public class GameScreen extends BaseScreen {

    public enum State {
        PLAYING, PAUSED, LEVEL_COMPLETED, GAME_OVER, WIN
    }

    private InputMultiplexer inputMultiplexer;

    private State state;

    private int lives;
    private int totalScore;
    private int levelIndex;
    Level level;

    private StatusBar statusBar;
    private Cell levelCell;
    private Cell statusCell;
    private NotificationLabel pointsLabel;
    private NotificationLabel bigPointsLabel;
    private NotificationLabel notificationLabel;
    private AlertDialog alertDialog;
    private Background background;

    public GameScreen(final AxonixGame game) {
        super(game);
        setState(State.PAUSED);
        // Input event handling
        inputMultiplexer = new InputMultiplexer();
        inputMultiplexer.addProcessor(new GameScreenInputProcessor(game, this));
        inputMultiplexer.addProcessor(stage);
        // init subcomponents
        Style style = getStyleByHeight(Gdx.graphics.getHeight());
        Table rootTable = initRootTable(style);
        DebugBar debugBar = initDebugBar();
        initBackground();
        initPointsLabels(style);
        initNotificationLabel(style);
        initAlertDialog(style);
        // add subcomponents to stage
        stage.addActor(background);
        stage.addActor(rootTable);
        stage.addActor(pointsLabel);
        stage.addActor(bigPointsLabel);
        stage.addActor(notificationLabel);
        stage.addActor(alertDialog);
        stage.addActor(debugBar);
    }

    public void nextLevel() {
        setLevel(levelIndex + 1, false);
    }

    public void loadLevel(int index) {
        setLevel(index, true);
    }

    @Override
    public void render(float delta) {
        this.act(delta);
        super.render(delta);
    }

    @Override
    public void resize(int width, int height) {
        stage.setViewport(width, height, false);
        float scale = calculateScaling(stage, level, statusCell.getMaxHeight());
        level.setScale(scale);
        levelCell.width(level.getWidth() * scale).height(level.getHeight() * scale);
        background.update(true);

        Style style = getStyleByHeight(height);
        BitmapFont font = skin.getFont(style.toString());

        statusCell.height(font.getLineHeight());
        statusBar.setFont(font);
        pointsLabel.setFont(font);
        bigPointsLabel.setFont(style.getNext().toString());
        notificationLabel.setFont(font);
        alertDialog.setStyle(style.toString());
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(inputMultiplexer);
    }

    @Override
    public void pause() {
        setState(State.PAUSED);
    }

    public boolean isInState(State state) {
        return this.state == state;
    }

    //---------------------------------------------------------------------
    // Helper methods
    //---------------------------------------------------------------------

    private void initBackground() {
        background = new Background(skin);
    }

    private Table initRootTable(Style style) {
        Table rootTable = new Table();
        rootTable.setFillParent(true);
        // status bar
        statusBar = new StatusBar(this, skin, style.toString());
        statusCell = rootTable.add(statusBar);
        statusCell.height(skin.getFont(style.toString()).getLineHeight()).left();
        rootTable.row();
        // level cell
        levelCell = rootTable.add();
        return  rootTable;
    }

    private void initPointsLabels(Style style) {
        pointsLabel = new NotificationLabel(null, skin, style.toString());
        bigPointsLabel = new NotificationLabel(null, skin, style.getNext().toString());
    }

    private void initNotificationLabel(Style style) {
        notificationLabel = new NotificationLabel(null, skin, style.toString());
        notificationLabel.setFillParent(true);
        notificationLabel.setAlignment(Align.center);
    }

    private void initAlertDialog(Style style) {
        alertDialog = new AlertDialog(null, skin, style.toString());
        // levels button listener
        alertDialog.addButtonListener(1, new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                game.setLevelsScreen();
            }
        });
        // replay button listener
        alertDialog.addButtonListener(2, new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                game.setGameScreen(levelIndex);
            }
        });
        // forward button listener
        alertDialog.addButtonListener(3, new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                switch (getState()) {
                    case PAUSED:
                        setState(GameScreen.State.PLAYING);
                        break;
                    case LEVEL_COMPLETED:
                        int nextIndex = getLevelIndex() + 1;
                        if (nextIndex <= game.getLevelsFiles().size()) {
                            nextLevel();
                        } else {
                            setState(GameScreen.State.WIN);
                        }
                        break;
                    case GAME_OVER:
                    case WIN:
                        game.setStartScreen();
                        break;
                }
            }
        });
    }

    private DebugBar initDebugBar() {
        return new DebugBar(skin, Style.SMALL.toString());
    }

    private void act(float delta) {
        check();
        showNotifications();
    }

    private void check() {
        // check lives
        if (lives <= 0 && !isInState(State.GAME_OVER)) {
            setState(State.GAME_OVER);
        }
    }

    private void showNotifications() {
        switch (state) {
            case PLAYING:
                // hide notifications
                if (notificationLabel.getActions().size == 0 || alertDialog.isVisible()) {
                    notificationLabel.clearActions();
                    notificationLabel.setVisible(false);
                }
                if (alertDialog.getActions().size == 0) {
                    alertDialog.setVisible(false);
                }
                break;
            case PAUSED:
                alertDialog.setTitle("PAUSE");
                alertDialog.setScores(getLevel().getLevelScore(), getTotalScore() + getLevel().getLevelScore());
                alertDialog.setVisible(true);
                break;
            case LEVEL_COMPLETED:
                alertDialog.setTitle("LEVEL COMPLETED");
                alertDialog.setScores(getLevel().getLevelScore(), getTotalScore());
                alertDialog.setVisible(true);
                break;
            case GAME_OVER:
                alertDialog.setTitle("GAME OVER");
                alertDialog.setScores(getLevel().getLevelScore(), getTotalScore());
                alertDialog.setVisible(true);
                break;
            case WIN:
                alertDialog.setTitle("YOU WIN!");
                alertDialog.setScores(getLevel().getLevelScore(), getTotalScore());
                alertDialog.setVisible(true);
                break;
        }
    }

    private void onStateChanged() {
        switch (state) {
            case LEVEL_COMPLETED:
                setTotalScore(getTotalScore() + level.getLevelScore());
                saveLevelInfoToPrefs();
                break;
            case GAME_OVER:
            case WIN:
                setTotalScore(getTotalScore() + level.getLevelScore());
                saveGameInfoToPrefs();
                break;
        }
    }

    private void setLevel(int index, boolean loadFromPrefs) {
        this.levelIndex = index;
        // init level structure from pixmap
        Pixmap pixmap = new Pixmap(game.getLevelsFiles().get(index - 1));
        level = new Level(this, pixmap, skin);
        // set widget size
        float scale = calculateScaling(stage, level, statusCell.getMaxHeight());
        level.setScale(scale);
        levelCell.setWidget(level).width(level.getWidth() * scale).height(level.getHeight() * scale);
        // get level info from preferences
        if (loadFromPrefs) {
            loadLevelInfoFromPrefs(index - 1);
            setTotalScore(0);
        }
        // go play
        setState(State.PLAYING);
    }

    private void loadLevelInfoFromPrefs(int levelNumber) {
        Preferences prefs = game.getPreferences();
        if (levelNumber == 0) {
            setLives(3);
        } else if (prefs.contains(AxonixGame.PREF_KEY_LIVES + levelNumber)) {
            int savedLivesNumber = prefs.getInteger(AxonixGame.PREF_KEY_LIVES + levelNumber);
            setLives(savedLivesNumber);
        } else {
            throw new IllegalArgumentException("Preferences do not contain values for index:" + levelNumber);
        }
    }

    private void saveLevelInfoToPrefs() {
        Preferences prefs = game.getPreferences();
        boolean prefsChanged = false;

        int savedLivesNumber = prefs.getInteger(AxonixGame.PREF_KEY_LIVES + levelIndex);
        int savedLevelScore = prefs.getInteger(AxonixGame.PREF_KEY_LVL_SCORE + levelIndex);

        int newLivesNumber = getLives();
        int newLevelScore = level.getLevelScore();

        if (newLivesNumber > savedLivesNumber) {
            prefs.putInteger(AxonixGame.PREF_KEY_LIVES + levelIndex, newLivesNumber);
            prefsChanged = true;
        }
        if (newLevelScore > savedLevelScore) {
            prefs.putInteger(AxonixGame.PREF_KEY_LVL_SCORE + levelIndex, newLevelScore);
            prefsChanged = true;
        }

        if (prefsChanged) {
            prefs.flush();
        }
    }

    private void saveGameInfoToPrefs() {
        Preferences prefs = game.getPreferences();
        int savedTotalScore = prefs.getInteger(AxonixGame.PREF_KEY_TTL_SCORE);
        int newTotalScore = getTotalScore();
        if (newTotalScore > savedTotalScore) {
            prefs.putInteger(AxonixGame.PREF_KEY_TTL_SCORE, newTotalScore);
            prefs.flush();
        }
    }

    private float calculateScaling(Stage stage, Level level, float statusBarHeight) {
        int padding = 5;
        float wScaling = (stage.getWidth() - padding)/ level.getWidth();
        float hScaling = (stage.getHeight() - statusBarHeight - padding) / level.getHeight();
        return min(wScaling, hScaling);
    }

    //---------------------------------------------------------------------
    // Getters & Setters
    //---------------------------------------------------------------------

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
        onStateChanged();
    }

    public int getLevelIndex() {
        return levelIndex;
    }

    public void setLevelIndex(int levelIndex) {
        this.levelIndex = levelIndex;
    }

    public int getLives() {
        return lives;
    }

    public void setLives(int lives) {
        this.lives = lives;
    }

    public NotificationLabel getNotificationLabel() {
        return notificationLabel;
    }

    public void setNotificationLabel(NotificationLabel notificationLabel) {
        this.notificationLabel = notificationLabel;
    }

    public StatusBar getStatusBar() {
        return statusBar;
    }

    public void setStatusBar(StatusBar statusBar) {
        this.statusBar = statusBar;
    }

    public Level getLevel() {
        return level;
    }

    public void setLevel(Level level) {
        this.level = level;
    }

    public int getTotalScore() {
        return totalScore;
    }

    public void setTotalScore(int totalScore) {
        this.totalScore = totalScore;
    }

    public NotificationLabel getPointsLabel() {
        return pointsLabel;
    }

    public void setPointsLabel(NotificationLabel pointsLabel) {
        this.pointsLabel = pointsLabel;
    }

    public NotificationLabel getBigPointsLabel() {
        return bigPointsLabel;
    }

    public void setBigPointsLabel(NotificationLabel bigPointsLabel) {
        this.bigPointsLabel = bigPointsLabel;
    }

}