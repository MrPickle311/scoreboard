package com.example.scoreboard.storage;

class InMemoryMatchRepositoryTest extends MatchRepositoryContractTest {

    @Override
    protected MatchRepository createRepository() {
        return new InMemoryMatchRepository();
    }
}
