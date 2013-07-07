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

package net.ivang.axonix.main.actors.levels;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.google.common.eventbus.EventBus;
import net.ivang.axonix.main.events.facts.ButtonClickFact;
import net.ivang.axonix.main.events.intents.screen.GameScreenIntent;

/**
 * @author Ivan Gadzhega
 * @since 0.1
 */
public class LevelButton extends TextButton {

    private final int levelIndex;

    public LevelButton(final int levelIndex, TextButtonStyle style, final EventBus eventBus) {
        super(Integer.toString(levelIndex), style);
        this.levelIndex = levelIndex;
        addListener(new ChangeListener() {
            public void changed(ChangeEvent event, Actor actor) {
                eventBus.post(new ButtonClickFact());
                eventBus.post(new GameScreenIntent(levelIndex));
            }
        });
    }

    //---------------------------------------------------------------------
    // Getters & Setters
    //---------------------------------------------------------------------

    public int getLevelIndex() {
        return levelIndex;
    }
}