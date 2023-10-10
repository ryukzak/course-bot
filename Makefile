NAME = csa-bot
CONF = ${PWD}/../edu-csa-internal
DB = ${PWD}/../csa-db-2023

BACKUP_PATH = ..

NOW = $(shell date +'%Y-%m-%d-%H-%M')

update: pull backup build stop run

pull:
	git pull

backup:
	tar -zcf "${BACKUP_PATH}/csa-db-snapshot-${NOW}.tar.gz" -C ${DB} .
#   file should be more than 100 Kb
	[ `stat -c %s "${BACKUP_PATH}/csa-db-snapshot-${NOW}.tar.gz"` -gt 100 ]

build:
	docker build -t ${NAME} .

stop:
	docker stop ${NAME}
	docker rm ${NAME}

run:
	docker run --name ${NAME} --restart=always -d -v ${CONF}:/edu-csa-internal -v ${DB}:/csa-db ${NAME}

clean:
	rm -f *.csv
	find . -type f -name '*-lab1-descriptions.md' -delete
	rm -f *.jar
	rm -rf codax-db-test
	rm -rf test-databases

repl:
	clj -M:test:nrepl
