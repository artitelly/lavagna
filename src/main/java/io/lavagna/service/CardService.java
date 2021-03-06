/**
 * This file is part of lavagna.
 *
 * lavagna is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * lavagna is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with lavagna.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.lavagna.service;

import io.lavagna.model.Card;
import io.lavagna.model.CardDataCount;
import io.lavagna.model.CardFull;
import io.lavagna.model.CardFullWithCounts;
import io.lavagna.model.CardFullWithCountsHolder;
import io.lavagna.model.Event;
import io.lavagna.model.Event.EventType;
import io.lavagna.model.LabelAndValue;
import io.lavagna.model.User;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.commons.lang3.builder.CompareToBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CardService {

	private final EventRepository eventRepository;
	private final CardRepository cardRepository;
	private final CardDataRepository cardDataRepository;
	private final CardLabelRepository cardLabelRepository;

	@Autowired
	public CardService(CardRepository cardRepository, CardDataRepository cardDataRepository,
			EventRepository eventRepository, CardLabelRepository cardLabelRepository) {
		this.cardRepository = cardRepository;
		this.eventRepository = eventRepository;
		this.cardDataRepository = cardDataRepository;
		this.cardLabelRepository = cardLabelRepository;
	}

	private static List<Integer> fetchIds(List<CardFull> cards) {
		List<Integer> r = new ArrayList<>(cards.size());
		for (CardFull c : cards) {
			r.add(c.getId());
		}
		return r;
	}

	public List<CardFullWithCounts> fetchAllInColumn(int columnId) {

		List<CardFull> cards = cardRepository.findAllByColumnId(columnId);
		if (cards.isEmpty()) {
			return Collections.emptyList();
		}
		List<CardFullWithCounts> res = fetchCardFull(cards);

		Collections.sort(res, new Comparator<CardFullWithCounts>() {
			@Override
			public int compare(CardFullWithCounts o1, CardFullWithCounts o2) {
				return new CompareToBuilder().append(o1.getOrder(), o2.getOrder()).toComparison();
			}
		});
		//
		return res;
	}

	public CardFullWithCountsHolder getAllOpenCards(User user, int page, int pageSize) {

		List<CardFull> cards = cardRepository.fetchAllOpenCardsByUserId(user.getId(), page, pageSize);
		if (cards.isEmpty()) {
			return new CardFullWithCountsHolder(Collections.<CardFullWithCounts> emptyList(), 0, 0);
		}
		List<CardFullWithCounts> res = fetchCardFull(cards);

		int totalItems = 0;
		if ((page == 0 && cards.size() > pageSize) || (page > 0 && !cards.isEmpty())) {
			totalItems = cardRepository.getOpenCardsCountByUserId(user.getId());
		} else {
			totalItems = cards.size();
		}

		return new CardFullWithCountsHolder(res, totalItems, pageSize);
	}

	public CardFullWithCountsHolder getAllOpenCardsByProject(String projectShortName, User user, int page, int pageSize) {
		List<CardFull> cards = cardRepository.fetchAllOpenCardsByProjectAndUserId(projectShortName, user.getId(), page,
				pageSize);
		if (cards.isEmpty()) {
			return new CardFullWithCountsHolder(Collections.<CardFullWithCounts> emptyList(), 0, 0);
		}
		List<CardFullWithCounts> res = fetchCardFull(cards);

		int totalItems = 0;
		if ((page == 0 && cards.size() > pageSize) || (page > 0 && !cards.isEmpty())) {
			totalItems = cardRepository.getOpenCardsCountByProjectAndUserId(projectShortName, user.getId());
		} else {
			totalItems = cards.size();
		}

		return new CardFullWithCountsHolder(res, totalItems, pageSize);
	}

	List<CardFullWithCounts> fetchCardFull(List<CardFull> cards) {
		List<Integer> ids = fetchIds(cards);
		Map<Integer, Map<String, CardDataCount>> counts = aggregateByCardId(cardDataRepository.findCountsByCardIds(ids));
		Map<Integer, List<LabelAndValue>> labels = cardLabelRepository.findCardLabelValuesByCardIds(ids);
		List<CardFullWithCounts> res = new ArrayList<>();
		for (CardFull card : cards) {
			res.add(new CardFullWithCounts(card, counts.get(card.getId()), labels.get(card.getId())));
		}
		return res;
	}

	@Transactional(readOnly = false)
	public void moveCardsToColumn(List<Integer> cardIds, int previousColumnId, int columnId, int userId,
			EventType boardEventType, Date time) {
		List<Integer> updated = cardRepository.moveCardsToColumn(cardIds, previousColumnId, columnId, userId);
		eventRepository.insertCardEvents(updated, previousColumnId, columnId, userId, boardEventType, time, null);
	}

	@Transactional(readOnly = false)
	public Event updateCard(int cardId, String name, User user, Date date) {
		Card card = cardRepository.updateCard(cardId, name, user);
		return eventRepository.insertCardEvent(cardId, card.getColumnId(), user.getId(), EventType.CARD_UPDATE, date,
				name);
	}

	@Transactional(readOnly = false)
	public Card createCard(String name, int columnId, Date creationTime, User user) {
		Card card = cardRepository.createCard(name, columnId, user);
		eventRepository.insertCardEvent(card.getId(), columnId, user.getId(), EventType.CARD_CREATE, creationTime,
				card.getName());
		return card;
	}

	@Transactional(readOnly = false)
	public Card createCardFromTop(String name, int columnId, Date creationTime, User user) {
		Card card = cardRepository.createCardFromTop(name, columnId, user);
		eventRepository.insertCardEvent(card.getId(), columnId, user.getId(), EventType.CARD_CREATE, creationTime,
				card.getName());
		return card;
	}

	@Transactional(readOnly = false)
	public Event moveCardToColumn(int cardId, int previousColumnId, int columnId, int userId, Date date) {
		cardRepository.moveCardToColumn(cardId, previousColumnId, columnId);
		return eventRepository.insertCardEvent(cardId, previousColumnId, columnId, userId, EventType.CARD_MOVE, date,
				null);
	}

	@Transactional(readOnly = false)
	public Event moveCardToColumnAndReorder(int cardId, int prevColumnId, int newColumnId,
			List<Integer> newOrderForNewColumn, User user) {
		cardRepository.moveCardToColumnAndReorder(cardId, prevColumnId, newColumnId, newOrderForNewColumn);
		return eventRepository.insertCardEvent(cardId, prevColumnId, newColumnId, user.getId(), EventType.CARD_MOVE,
				new Date(), null);
	}

	private static Map<Integer, Map<String, CardDataCount>> aggregateByCardId(List<CardDataCount> counts) {
		Map<Integer, Map<String, CardDataCount>> r = new TreeMap<>();

		for (CardDataCount c : counts) {
			if (!r.containsKey(c.getCardId())) {
				r.put(c.getCardId(), new TreeMap<String, CardDataCount>());
			}
			r.get(c.getCardId()).put(c.getType(), c);
		}

		return r;
	}

}
