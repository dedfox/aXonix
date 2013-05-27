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
import com.badlogic.gdx.scenes.scene2d.Action;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.actions.ParallelAction;
import com.badlogic.gdx.scenes.scene2d.actions.SequenceAction;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.Align;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.esotericsoftware.tablelayout.Cell;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.inject.Inject;
import net.ivang.axonix.main.AxonixGame;
import net.ivang.axonix.main.screen.BaseScreen;
import net.ivang.axonix.main.screen.game.actor.Level;
import net.ivang.axonix.main.screen.game.actor.background.Background;
import net.ivang.axonix.main.screen.game.actor.bar.DebugBar;
import net.ivang.axonix.main.screen.game.actor.bar.StatusBar;
import net.ivang.axonix.main.screen.game.actor.dialog.AlertDialog;
import net.ivang.axonix.main.screen.game.actor.notification.NotificationLabel;
import net.ivang.axonix.main.screen.game.event.*;
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
    private EventBus eventBus;

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

    @Inject
    private GameScreen(final AxonixGame game, EventBus eventBus) {
        super(game);
        // register with the event bus
        this.eventBus = eventBus;
        eventBus.register(this);
        setState(State.PAUSED);
        // Input event handling
        inputMultiplexer = new InputMultiplexer();
        inputMultiplexer.addProcessor(new GameScreenInputProcessor(game, this));
        inputMultiplexer.addProcessor(stage);
        // init sub-components
        Style style = getStyleByHeight();
        Table rootTable = initRootTable(style);
        DebugBar debugBar = initDebugBar();
        initBackground();
        initPointsLabels(style);
        initNotificationLabel(style);
        initAlertDialog(style);
        // add sub-components to stage
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

    //---------------------------------------------------------------------
    // Subscribers
    //---------------------------------------------------------------------

    @Subscribe
    @SuppressWarnings("unused")
    public void onStateChange(State state) {
        switch (state) {
            case LEVEL_COMPLETED:
                saveLevelInfoToPrefs();
                break;
            case GAME_OVER:
            case WIN:
                saveGameInfoToPrefs();
                break;
        }
    }

    @Subscribe
    @SuppressWarnings("unused")
    public void loadLevel(LoadLevelAction event) {
        loadLevel(event.getLevelIndex());
    }

    @Subscribe
    @SuppressWarnings("unused")
    public void onLevelStateChange(Level.State levelState) {
        switch (levelState) {
            case LEVEL_COMPLETED:
                setState(State.LEVEL_COMPLETED);
                break;
        }
    }

    @Subscribe
    @SuppressWarnings("unused")
    public void onLevelScoreChange(LevelScoreEvent event) {
        alertDialog.setLevelScore(event.getScore());
    }

    @Subscribe
    @SuppressWarnings("unused")
    public void onLevelScoreChange(LevelScoreDeltaEvent event) {
        setTotalScore(getTotalScore() + event.getScoreDelta());
    }

    @Subscribe
    @SuppressWarnings("unused")
    public void onTotalScoreChange(TotalScoreEvent event) {
        alertDialog.setTotalScore(event.getScore());
    }

    @Subscribe
    @SuppressWarnings("unused")
    public void changeLivesNumber(LivesDeltaEvent event) {
        setLives(getLives() + event.getLivesDelta());
    }

    @Subscribe
    @SuppressWarnings("unused")
    public void showNotification(NotificationEvent event) {
        Action action = Actions.sequence(Actions.delay(event.getShowDelay()),
                                         Actions.show(),
                                         Actions.delay(event.getHideDelay()),
                                         Actions.hide());
        getNotificationLabel().setText(event.getMessage());
        getNotificationLabel().addAction(action);
    }

    @Subscribe
    @SuppressWarnings("unused")
    public void showObtainedPoints(ObtainedPointsEvent event) {
        // text
        int points = event.getPoints();
        NotificationLabel label = (points <= 200) ? getPointsLabel(): getBigPointsLabel();
        label.setText(Integer.toString(points));
        // position
        float x = event.getX();
        float y = event.getY();
        if (event.isSubtractBounds()) {
            x -= label.getTextBounds().width;
        }
        label.setPosition(x, y);
        // actions
        SequenceAction fadeInFadeOut = Actions.sequence(Actions.visible(true), Actions.fadeIn(0.2f), Actions.delay(1f),
                Actions.fadeOut(0.3f), Actions.visible(false));
        ParallelAction fadeAndMove = Actions.parallel(Actions.moveTo(x, y + event.getDeltaY(), 1.5f), fadeInFadeOut);
        label.clearActions();
        label.addAction(fadeAndMove);
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
        statusBar = new StatusBar(this, eventBus, skin, style.toString());
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
        showAlertDialog();
    }

    private void check() {
        // check lives
        if (lives <= 0 && !isInState(State.GAME_OVER)) {
            setState(State.GAME_OVER);
        }
    }

    private void showAlertDialog() {
        switch (state) {
            case PLAYING:
                // hide dialog
                if (alertDialog.getActions().size == 0) {
                    alertDialog.setVisible(false);
                }
                break;
            case PAUSED:
                alertDialog.setTitle("PAUSE");
                alertDialog.setVisible(true);
                break;
            case LEVEL_COMPLETED:
                alertDialog.setTitle("LEVEL COMPLETED");
                alertDialog.setVisible(true);
                break;
            case GAME_OVER:
                alertDialog.setTitle("GAME OVER");
                alertDialog.setVisible(true);
                break;
            case WIN:
                alertDialog.setTitle("YOU WIN!");
                alertDialog.setVisible(true);
                break;
        }
    }

    private void setLevel(int index, boolean loadFromPrefs) {
        if (level != null) eventBus.unregister(level);
        // init level structure from pixmap
        Pixmap pixmap = new Pixmap(game.getLevelsFiles().get(index - 1));
        level = new Level(index, pixmap, skin, eventBus);
        levelIndex = index;
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
        int newLevelScore = level.getScore();

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

    private boolean isInState(State state) {
        return this.state == state;
    }

    //---------------------------------------------------------------------
    // Getters & Setters
    //---------------------------------------------------------------------

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
        eventBus.post(state);
    }

    public int getLevelIndex() {
        return levelIndex;
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

    public StatusBar getStatusBar() {
        return statusBar;
    }

    public Level getLevel() {
        return level;
    }

    public int getTotalScore() {
        return totalScore;
    }

    public void setTotalScore(int totalScore) {
        this.totalScore = totalScore;
        eventBus.post(new TotalScoreEvent(totalScore));
    }

    public NotificationLabel getPointsLabel() {
        return pointsLabel;
    }

    public NotificationLabel getBigPointsLabel() {
        return bigPointsLabel;
    }

    public AlertDialog getAlertDialog() {
        return alertDialog;
    }
}