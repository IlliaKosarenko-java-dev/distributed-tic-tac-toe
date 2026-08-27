/*
 * Watches a game the microservices play against each other.
 *
 * The browser never decides a move. It creates a session, subscribes to that session's event
 * stream, asks for the simulation to start, and then only renders what the backend reports.
 */
(function () {
    'use strict';

    var API = '/api';

    var el = {
        start: document.getElementById('start'),
        board: document.getElementById('board'),
        status: document.getElementById('status'),
        log: document.getElementById('log'),
        error: document.getElementById('error'),
        session: document.getElementById('meta-session'),
        moves: document.getElementById('meta-moves'),
        xStrategy: document.getElementById('x-strategy'),
        oStrategy: document.getElementById('o-strategy'),
        delay: document.getElementById('move-delay')
    };

    var cells = [];
    var stream = null;

    function buildBoard() {
        for (var i = 0; i < 9; i++) {
            var cell = document.createElement('div');
            cell.className = 'cell';
            cell.setAttribute('role', 'gridcell');
            cell.dataset.index = String(i);
            el.board.appendChild(cell);
            cells.push(cell);
        }
    }

    function renderBoard(board, latestPosition) {
        for (var i = 0; i < 9; i++) {
            var mark = board[i];
            var cell = cells[i];
            cell.textContent = mark || '';
            cell.className = 'cell'
                + (mark ? ' cell--' + mark.toLowerCase() : '')
                + (i === latestPosition ? ' cell--latest' : '');
        }
    }

    function highlightWin(line) {
        (line || []).forEach(function (position) {
            cells[position].classList.add('cell--win');
            cells[position].classList.remove('cell--latest');
        });
    }

    function setStatus(text, variant) {
        el.status.textContent = text;
        el.status.className = 'status status--' + variant;
    }

    function showError(message) {
        el.error.textContent = message;
        el.error.hidden = false;
    }

    function clearError() {
        el.error.hidden = true;
        el.error.textContent = '';
    }

    function appendLog(parts) {
        var item = document.createElement('li');
        parts.forEach(function (part) {
            var span = document.createElement('span');
            span.className = part.className;
            span.textContent = part.text;
            item.appendChild(span);
        });
        el.log.appendChild(item);
        el.log.scrollTop = el.log.scrollHeight;
    }

    function describeOutcome(outcome) {
        if (outcome === 'X_WON') { return { text: 'X wins', variant: 'won' }; }
        if (outcome === 'O_WON') { return { text: 'O wins', variant: 'won' }; }
        if (outcome === 'DRAW') { return { text: 'Draw', variant: 'draw' }; }
        return { text: 'In progress', variant: 'running' };
    }

    function reset() {
        clearError();
        el.log.innerHTML = '';
        el.moves.textContent = '0';
        renderBoard(new Array(9).fill(null), -1);
        if (stream) {
            stream.close();
            stream = null;
        }
    }

    function finish() {
        el.start.disabled = false;
        el.start.textContent = 'Start Simulation';
        if (stream) {
            stream.close();
            stream = null;
        }
    }

    /* Subscribing before asking for the simulation to start; the other order can miss the
       opening moves, because the backend begins playing the moment it is asked. */
    function subscribe(sessionId) {
        stream = new EventSource(API + '/sessions/' + sessionId + '/events');

        stream.addEventListener('snapshot', function (event) {
            var data = JSON.parse(event.data);
            renderBoard(data.board, -1);
            el.moves.textContent = String(data.moveCount);
        });

        stream.addEventListener('status', function (event) {
            var data = JSON.parse(event.data);
            if (data.status === 'RUNNING') {
                setStatus('Playing…', 'running');
            }
        });

        stream.addEventListener('move', function (event) {
            var data = JSON.parse(event.data);
            renderBoard(data.board, data.position);
            el.moves.textContent = String(data.seq);
            appendLog([
                { className: 'seq', text: '#' + data.seq },
                { className: 'player--' + data.player, text: data.player },
                { className: 'note', text: 'cell ' + data.position }
            ]);
        });

        stream.addEventListener('finished', function (event) {
            var data = JSON.parse(event.data);
            var outcome = describeOutcome(data.outcome);
            setStatus(outcome.text + ' after ' + data.moveCount + ' moves', outcome.variant);
            highlightWin(data.winningLine);
            appendLog([{ className: 'note', text: '— ' + outcome.text + ' —' }]);
            finish();
        });

        stream.addEventListener('error', function (event) {
            /* Two different things arrive here: a failure the backend reported, and a
               transport error with no payload. Only the first has something to say. */
            if (event.data) {
                var data = JSON.parse(event.data);
                setStatus('Simulation failed', 'failed');
                showError(data.message || 'The simulation could not finish.');
                appendLog([{ className: 'note', text: '— ' + (data.code || 'error') + ' —' }]);
                finish();
            }
        });

        stream.onerror = function () {
            /* EventSource reconnects on its own; only complain once the game is over and the
               stream has actually been closed by the server. */
            if (stream && stream.readyState === EventSource.CLOSED) {
                finish();
            }
        };
    }

    function request(path, options) {
        return fetch(API + path, options).then(function (response) {
            if (!response.ok) {
                return response.json()
                    .catch(function () { return {}; })
                    .then(function (problem) {
                        throw new Error(problem.detail || problem.title
                            || ('Request failed with status ' + response.status));
                    });
            }
            return response.json();
        });
    }

    function start() {
        reset();
        el.start.disabled = true;
        el.start.textContent = 'Playing…';
        setStatus('Creating session…', 'running');

        request('/sessions', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                xStrategy: el.xStrategy.value,
                oStrategy: el.oStrategy.value,
                moveDelayMs: Number(el.delay.value)
            })
        }).then(function (session) {
            el.session.textContent = session.sessionId.slice(0, 8);
            subscribe(session.sessionId);
            return request('/sessions/' + session.sessionId + '/simulate', { method: 'POST' });
        }).catch(function (failure) {
            setStatus('Could not start', 'failed');
            showError(failure.message);
            finish();
        });
    }

    buildBoard();
    el.start.addEventListener('click', start);
})();
