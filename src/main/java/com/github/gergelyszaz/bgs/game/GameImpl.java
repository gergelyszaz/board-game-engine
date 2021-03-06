package com.github.gergelyszaz.bgs.game;

import com.google.common.collect.Lists;
import com.github.gergelyszaz.bgl.bgl.Field;
import com.github.gergelyszaz.bgl.bgl.*;
import com.github.gergelyszaz.bgs.game.model.action.ActionManager;
import com.github.gergelyszaz.bgs.game.model.action.impl.SelectAction;
import com.github.gergelyszaz.bgs.game.model.Deck;
import com.github.gergelyszaz.bgs.game.model.Player;
import com.github.gergelyszaz.bgs.game.model.Token;
import com.github.gergelyszaz.bgs.state.*;
import com.github.gergelyszaz.bgs.state.util.StateStore;
import com.github.gergelyszaz.bgs.view.*;
import java.util.*;

public class GameImpl implements Controller, Game {

	private String name;
	private VariableManager variableManager;
	private ActionManager actionManager;
	private IDManager idManager;
	private StateStore stateStore;
	private InternalManager internalManager;
	private Set<View> views = new HashSet<>();

	public GameImpl(VariableManager variableManager, ActionManager actionManager, IDManager idManager,
			StateStore stateStore, InternalManager internalManager) {
		this.internalManager = internalManager;
		this.variableManager = variableManager;
		this.actionManager = actionManager;
		this.idManager = idManager;
		this.stateStore = stateStore;

	}

	public VariableManager getVariableManager() {
		return variableManager;
	}

	public InternalManager getInternalManager() {
		return internalManager;
	}

	@Override
	public boolean Join(View view) throws IllegalAccessException {
		addView(view);
		
		for (Player player : internalManager.getPlayers()) {
			if (!player.IsConnected()) {
				player.setSessionID(view.getId());
				_saveCurrentState();
				return true;
			}
		}
		return false;
	}

	@Override
	public void Disconnect(String clientID) {
		Player player = internalManager.getPlayers().stream().filter(p -> p.getSessionID().equals(clientID)).findAny()
				.get();

		internalManager.getLosers().add(player);
	}

	@Override
	public boolean IsFull() {

		boolean isFull = true;
		for (Player player : internalManager.getPlayers()) {
			isFull &= player.IsConnected();
		}
		return isFull;
	}

	public void Init(Model gameModel, List<Player> players, List<Deck> decks) {

		name = gameModel.getName();
		internalManager.getFields().addAll(gameModel.getFields());
		internalManager.getPlayers().addAll(players);
		internalManager.getDecks().addAll(decks);

		internalManager.setCurrentPlayer(players.get(0), variableManager);

	}

	@Override
	public void Step() throws IllegalAccessException {
		if (!internalManager.getSelectableManager().notWaitingForSelection() || IsFinished()) {
			return;
		}
		actionManager.step();
		actionManager.getCurrentAction().Execute();
		_saveCurrentState();
		views.forEach(View::Refresh);
	}

	@Override
	public void Start() throws IllegalAccessException {
		_saveCurrentState();
	}

	@Override
	public boolean IsFinished() {

		List<Player> winners = internalManager.getWinners();
		List<Player> losers = internalManager.getLosers();
		List<Player> players = Lists.newArrayList(internalManager.getPlayers());

		players.removeAll(winners);
		players.removeAll(losers);
		return players.size() < 2 && !(players.size() == 1 && internalManager.getPlayers().size() == 1);
	}

	@Override
	public void addView(View v) {
		v.setController(this);
		views.add(v);
	}

	@Override
	public GameState getCurrentState(String sessionID) {
		GameState gs = stateStore.getCurrentState();
		Player p = internalManager.getPlayers().stream().filter(player -> player.getSessionID().equals(sessionID))
				.findFirst().get();
		return gs.getPublicState(idManager.get(p));
	}

	@Override
	public boolean setSelected(String playerID, int selectedID) {

		try {
			if (!Objects.equals(playerID, _getCurrentPlayer().getSessionID())) {
				return false;
			}

			if (!internalManager.getSelectableManager().getSelectableObjects().contains(idManager.get(selectedID)))
				return false;

			if (!(actionManager.getCurrentAction() instanceof SelectAction)) {
				return false;
			}

			Object object = idManager.get(selectedID);
			variableManager.store(null, internalManager.getSelectableManager().getSelectableName(), object);

			if (object instanceof Token) {
				for (Field f : internalManager.getFields()) {
					variableManager.store(f, VariableManager.GLOBAL.DISTANCE_FROM_SELECTED_TOKEN, -1);
				}
				_setupDistance(((Token) object).getField(), 0);
			}

			internalManager.getSelectableManager().finishSelection();
			return true;
		} catch (IllegalAccessException e) {
			System.out.println(variableManager.listVariables());
			e.printStackTrace();
			return false;
		}
	}

	private Player _getCurrentPlayer() throws IllegalAccessException {

		return (Player) variableManager.getReference(null, VariableManager.GLOBAL.CURRENTPLAYER);
	}

	private void _setupDistance(Field startingField, int distance) throws IllegalAccessException {
		int dist = variableManager.getValue(startingField, VariableManager.GLOBAL.DISTANCE_FROM_SELECTED_TOKEN);
		if ((dist > -1 && dist <= distance))
			return;
		variableManager.store(startingField, VariableManager.GLOBAL.DISTANCE_FROM_SELECTED_TOKEN, distance);
		for (Field field : startingField.getNeighbours()) {
			{
				_setupDistance(field, distance + 1);
			}
		}
	}

	private void _saveCurrentState() throws IllegalAccessException {
		int stateVersion = stateStore.getCurrentVersion() + 1;

		GameState gameState = GameStateFactory.createGameState(name, idManager, stateVersion, internalManager);

		stateStore.addState(gameState);
	}

	@Override
	public boolean IsNotWaitingForInput() {
		return internalManager.getSelectableManager().notWaitingForSelection();
	}

	@Override
	public void removeView(View v) {}

}
