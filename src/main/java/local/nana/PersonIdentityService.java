
package local.nana;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PersonIdentityService {

    private final PersonRepository personRepository;
    private final PersonAccountLinkRepository linkRepository;

    public PersonIdentityService(
            PersonRepository personRepository,
            PersonAccountLinkRepository linkRepository
    ) {
        this.personRepository = personRepository;
        this.linkRepository = linkRepository;
    }

    @Transactional
    public long getOrCreatePersonForAccount(
            long accountId,
            String displayName
    ) {
        Long existingPersonId =
                linkRepository.findPersonIdByAccountId(accountId);

        if (existingPersonId != null) {
            return existingPersonId;
        }

        long personId =
                personRepository.createPerson(displayName);

        linkRepository.linkAccountToPerson(personId, accountId);

        return personId;
    }
}
