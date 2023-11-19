NAME = csa-bot
CONF = ${PWD}/../edu-csa-internal
DB = ${PWD}/../csa-db-2023
PLAGIARISM_DB = ${PWD}/../plagiarism-db

BACKUP_PATH = ..

NOW = $(shell date +'%Y-%m-%d-%H-%M')


.PHONY: all format format-check lint test build run-jar run update pull backup docker-build docker-run docker-down clean repl

all: format test lint build


format:
	clojure -M:fmt fix

format-check:
	clojure -M:fmt check

lint:
	clojure -M:lint

test:
	clojure -X:test

build:
	clojure -X:build uber

run-jar: build
	java -jar target/csa-bot.jar

run:
	clojure -X course-bot.csa/-main


update: pull backup build docker-build docker-stop docker-run

pull:
	git pull

backup:
	tar -zcf "${BACKUP_PATH}/csa-db-snapshot-${NOW}.tar.gz" -C ${DB} .
#   file should be more than 100 Kb
	[ `stat -c %s "${BACKUP_PATH}/csa-db-snapshot-${NOW}.tar.gz"` -gt 100 ]

	tar -zcf "${BACKUP_PATH}/plagiarism-db-${NOW}.tar.gz" -C ${PLAGIARISM_DB} .
#   file should be more than 100 Kb
	[ `stat -c %s "${BACKUP_PATH}/plagiarism-db-${NOW}.tar.gz"` -gt 100 ]


docker-build: build
	docker build -t ${NAME} .

docker-run:
	docker run --name ${NAME} --restart=always -d -v ${CONF}:/edu-csa-internal -p 3100:3100 -v ${PLAGIARISM_DB}:/plagiarism-db -v ${DB}:/csa-db ${NAME}

docker-stop:
	docker stop ${NAME}
	docker rm ${NAME}

clean:
	rm -fvr logs
	rm -fvr target
	rm -fvr tmp

repl:
	clojure -M:test:nrepl
