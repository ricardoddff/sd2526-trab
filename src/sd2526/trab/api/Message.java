package sd2526.trab.api;

import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Set;

@Entity
public class Message {

	@Id
	private String id;
	private String sender;

	@ElementCollection(fetch = FetchType.EAGER)
	private Set<String> destination;

	private long creationTime;
	private String subject;

	@Column(length = 10000)
	private String contents;

	public Message() {
		this.destination = new HashSet<>();
	}

	public Message(String id, String sender, Set<String> destinations, String subject, String contents) {
		this.id = id;
		this.sender = sender;
		this.subject = subject;
		this.contents = contents;
		this.creationTime = System.currentTimeMillis();
		this.destination = new HashSet<>(destinations);
	}

	public String getSender() { return sender; }
	public void setSender(String sender) { this.sender = sender; }

	// Devolve a lista real para o Hibernate conseguir fazer as remoções!
	public Set<String> getDestination() { return destination; }
	public void setDestination(Set<String> destination) { this.destination = destination; }

	public long getCreationTime() { return creationTime; }
	public void setCreationTime(long creationTime) { this.creationTime = creationTime; }
	public String getSubject() { return subject; }
	public void setSubject(String subject) { this.subject = subject; }
	public String getContents() { return contents; }
	public void setContents(String contents) { this.contents = contents; }
	public String getId() { return id; }
	public void setId(String id) { this.id = id; }

	@Override
	public String toString() {
		return "Message{id=" + id + ", sender='" + sender + "', destination=" + destination + "}";
	}
}