import React, { useState, useEffect } from 'react';
import { useParams } from 'react-router';
import { useQuery } from '@tanstack/react-query';
import { Client } from '../../services/Services';
import { StandardLayout } from '../../layout/StandardLayout';
import { CommandButton, CommandGroup } from '../../layout/MenuBar';
import ChannelGeneralEditor from './ChannelGeneralEditor';
import ChannelScriptsEditor from './ChannelScriptsEditor';
import ChannelConnectorsList from './ChannelConnectorsList';
import type { components } from '../../../../oieapi-types/index.d.ts';
import saveIcon from '../../assets/icons/disk.png';

type Channel = components['schemas']['Channel'];

const ChannelEditPage: React.FC = () => {
  const { channelId } = useParams<{ channelId: string }>();
  const [channel, setChannel] = useState<Channel | null>(null);
  const [activeTab, setActiveTab] = useState<
    'general' | 'scripts' | 'connectors'
  >('general');

  const { data, isLoading, error } = useQuery({
    queryKey: ['channel', channelId],
    queryFn: async () => {
      const resp = await Client.GET('/channels/{channelId}', {
        params: {
          path: { channelId: channelId! },
        },
      });
      if (resp.error) throw resp.error;
      return resp.data;
    },
    enabled: !!channelId,
  });

  useEffect(() => {
    if (data) {
      setChannel(data);
    }
  }, [data]);

  const handleSave = async () => {
    if (!channel || !channelId) return;
    const resp = await Client.PUT('/channels/{channelId}', {
      params: {
        path: { channelId },
      },
      body: channel,
    });
    if (resp.error) {
      alert('Error saving channel: ' + JSON.stringify(resp.error));
    } else {
      alert('Channel saved successfully');
    }
  };

  const handleChannelChange = (updatedChannel: Channel) => {
    setChannel(updatedChannel);
  };

  if (isLoading) return <div className='container mt-5'>Loading...</div>;
  if (error)
    return (
      <div className='container mt-5 text-danger'>
        Error: {JSON.stringify(error)}
      </div>
    );
  if (!channel) return <div className='container mt-5'>No channel data</div>;

  const commands = (
    <CommandGroup title='Channel Actions'>
      <CommandButton title='Save' icon={saveIcon} onClick={handleSave} />
    </CommandGroup>
  );

  return (
    <StandardLayout title={`Edit Channel: ${channel.name}`} commands={commands}>
      <div className='container mt-3'>
        <ul className='nav nav-tabs mb-3'>
          <li className='nav-item'>
            <button
              className={`nav-link ${activeTab === 'general' ? 'active' : ''}`}
              onClick={() => setActiveTab('general')}
            >
              General
            </button>
          </li>
          <li className='nav-item'>
            <button
              className={`nav-link ${activeTab === 'scripts' ? 'active' : ''}`}
              onClick={() => setActiveTab('scripts')}
            >
              Scripts
            </button>
          </li>
          <li className='nav-item'>
            <button
              className={`nav-link ${
                activeTab === 'connectors' ? 'active' : ''
              }`}
              onClick={() => setActiveTab('connectors')}
            >
              Connectors
            </button>
          </li>
        </ul>

        <div className='tab-content'>
          {activeTab === 'general' && (
            <ChannelGeneralEditor
              channel={channel}
              onChange={handleChannelChange}
            />
          )}
          {activeTab === 'scripts' && (
            <ChannelScriptsEditor
              channel={channel}
              onChange={handleChannelChange}
            />
          )}
          {activeTab === 'connectors' && (
            <ChannelConnectorsList channel={channel} />
          )}
        </div>
      </div>
    </StandardLayout>
  );
};

export default ChannelEditPage;
